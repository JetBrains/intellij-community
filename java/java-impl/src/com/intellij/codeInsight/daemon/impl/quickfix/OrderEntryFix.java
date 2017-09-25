/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixActionRegistrar;
import com.intellij.codeInsight.daemon.impl.actions.AddImportAction;
import com.intellij.codeInsight.daemon.quickFix.ExternalLibraryResolver;
import com.intellij.codeInsight.daemon.quickFix.ExternalLibraryResolver.ExternalClassResolveResult;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiJavaModuleReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UAnnotation;
import org.jetbrains.uast.UImportStatement;
import org.jetbrains.uast.UastContextKt;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author cdr
 */
public abstract class OrderEntryFix implements IntentionAction, LocalQuickFix {
  protected OrderEntryFix() { }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  @Override
  @NotNull
  public String getName() {
    return getText();
  }

  @Override
  public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
    invoke(project, null, descriptor.getPsiElement().getContainingFile());
  }

  @Nullable
  public static List<LocalQuickFix> registerFixes(@NotNull QuickFixActionRegistrar registrar, @NotNull PsiReference reference) {
    PsiElement psiElement = reference.getElement();
    String shortReferenceName = reference.getRangeInElement().substring(psiElement.getText());

    Project project = psiElement.getProject();
    PsiFile containingFile = psiElement.getContainingFile();
    if (containingFile == null) return null;
    VirtualFile refVFile = containingFile.getVirtualFile();
    if (refVFile == null) return null;

    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    Module currentModule = fileIndex.getModuleForFile(refVFile);
    if (currentModule == null) return null;

    DependencyScope scope = fileIndex.isInTestSourceContent(refVFile) ? DependencyScope.TEST : DependencyScope.COMPILE;

    if (reference instanceof PsiJavaModuleReference) {
      List<LocalQuickFix> result = ContainerUtil.newSmartList();
      createModuleFixes((PsiJavaModuleReference)reference, currentModule, scope, result);
      result.forEach(fix -> registrar.register((IntentionAction)fix));
      return result;
    }

    List<LocalQuickFix> result = ContainerUtil.newSmartList();
    JavaPsiFacade facade = JavaPsiFacade.getInstance(psiElement.getProject());

    registerExternalFixes(reference, psiElement, shortReferenceName, facade, currentModule, scope, registrar, result);
    if (!result.isEmpty()) {
      return result;
    }

    PsiClass[] classes = PsiShortNamesCache.getInstance(project).getClassesByName(shortReferenceName, GlobalSearchScope.allScope(project));
    List<PsiClass> allowedDependencies = filterAllowedDependencies(psiElement, classes);
    if (allowedDependencies.isEmpty()) {
      return result;
    }

    OrderEntryFix moduleDependencyFix = new AddModuleDependencyFix(reference, currentModule, scope, allowedDependencies);
    registrar.register(moduleDependencyFix);
    result.add(moduleDependencyFix);

    Set<Object> librariesToAdd = new THashSet<>();
    ModuleFileIndex moduleFileIndex = ModuleRootManager.getInstance(currentModule).getFileIndex();
    for (PsiClass aClass : allowedDependencies) {
      if (!facade.getResolveHelper().isAccessible(aClass, psiElement, aClass)) continue;
      PsiFile psiFile = aClass.getContainingFile();
      if (psiFile == null) continue;
      VirtualFile virtualFile = psiFile.getVirtualFile();
      if (virtualFile == null) continue;
      for (OrderEntry orderEntry : fileIndex.getOrderEntriesForFile(virtualFile)) {
        if (orderEntry instanceof LibraryOrderEntry) {
          final LibraryOrderEntry libraryEntry = (LibraryOrderEntry)orderEntry;
          final Library library = libraryEntry.getLibrary();
          if (library == null) continue;
          VirtualFile[] files = library.getFiles(OrderRootType.CLASSES);
          if (files.length == 0) continue;
          final VirtualFile jar = files[0];

          if (jar == null || libraryEntry.isModuleLevel() && !librariesToAdd.add(jar) || !librariesToAdd.add(library)) continue;
          OrderEntry entryForFile = moduleFileIndex.getOrderEntryForFile(virtualFile);
          if (entryForFile != null &&
              !(entryForFile instanceof ExportableOrderEntry &&
                ((ExportableOrderEntry)entryForFile).getScope() == DependencyScope.TEST &&
                !moduleFileIndex.isInTestSourceContent(refVFile))) {
            continue;
          }

          OrderEntryFix fix = new AddLibraryDependencyFix(reference, currentModule, library, scope, false, aClass.getQualifiedName());
          registrar.register(fix);
          result.add(fix);
        }
      }
    }

    return result;
  }

  private static void createModuleFixes(PsiJavaModuleReference reference,
                                        Module currentModule,
                                        DependencyScope scope,
                                        List<LocalQuickFix> result) {
    ProjectFileIndex index = ProjectRootManager.getInstance(currentModule.getProject()).getFileIndex();
    List<PsiElement> targets = Stream.of(reference.multiResolve(true))
      .map(ResolveResult::getElement)
      .filter(Objects::nonNull)
      .collect(Collectors.toList());

    PsiElement statement = reference.getElement().getParent();
    boolean exported = statement instanceof PsiRequiresStatement &&
                       ((PsiRequiresStatement)statement).hasModifierProperty(PsiModifier.TRANSITIVE);

    Set<Module> modules = targets.stream()
      .map(e -> !(e instanceof PsiCompiledElement) ? e.getContainingFile() : null)
      .map(f -> f != null ? f.getVirtualFile() : null)
      .filter(vf -> vf != null && index.isInSource(vf))
      .map(vf -> index.getModuleForFile(vf))
      .filter(m -> m != null && m != currentModule)
      .collect(Collectors.toSet());
    if (!modules.isEmpty()) {
      result.add(0, new AddModuleDependencyFix(reference, currentModule, modules, scope, exported));
    }

    targets.stream()
      .map(e -> e instanceof PsiCompiledElement ? e.getContainingFile() : null)
      .map(f -> f != null ? f.getVirtualFile() : null)
      .flatMap(vf -> vf != null ? index.getOrderEntriesForFile(vf).stream() : Stream.empty())
      .map(e -> e instanceof LibraryOrderEntry ? ((LibraryOrderEntry)e).getLibrary() : null)
      .filter(Objects::nonNull)
      .distinct()
      .forEach(l -> result.add(new AddLibraryDependencyFix(reference, currentModule, l, scope, exported, null)));
  }

  private static void registerExternalFixes(PsiReference reference,
                                            PsiElement psiElement,
                                            String shortReferenceName,
                                            JavaPsiFacade facade,
                                            Module currentModule,
                                            DependencyScope scope,
                                            QuickFixActionRegistrar registrar,
                                            List<LocalQuickFix> result) {
    String fullReferenceText = reference.getCanonicalText();
    ThreeState refToAnnotation = isReferenceToAnnotation(psiElement);
    for (ExternalLibraryResolver resolver : ExternalLibraryResolver.EP_NAME.getExtensions()) {
      ExternalClassResolveResult resolveResult = resolver.resolveClass(shortReferenceName, refToAnnotation, currentModule);
      OrderEntryFix fix = null;
      if (resolveResult != null &&
          facade.findClass(resolveResult.getQualifiedClassName(), currentModule.getModuleWithDependenciesAndLibrariesScope(true)) == null) {
        fix = new AddExtLibraryDependencyFix(reference, currentModule, resolveResult.getLibrary(), scope, resolveResult.getQualifiedClassName());
      }
      else if (!fullReferenceText.equals(shortReferenceName)) {
        ExternalLibraryDescriptor descriptor = resolver.resolvePackage(fullReferenceText);
        if (descriptor != null) {
          fix = new AddExtLibraryDependencyFix(reference, currentModule, descriptor, scope, null);
        }
      }
      if (fix != null) {
        registrar.register(fix);
        result.add(fix);
      }
    }
  }

  private static List<PsiClass> filterAllowedDependencies(PsiElement element, PsiClass[] classes) {
    DependencyValidationManager dependencyValidationManager = DependencyValidationManager.getInstance(element.getProject());
    PsiFile fromFile = element.getContainingFile();
    List<PsiClass> result = new ArrayList<>();
    for (PsiClass psiClass : classes) {
      PsiFile containingFile = psiClass.getContainingFile();
      if (containingFile != null && dependencyValidationManager.getViolatorDependencyRule(fromFile, containingFile) == null) {
        result.add(psiClass);
      }
    }
    return result;
  }

  private static ThreeState isReferenceToAnnotation(final PsiElement psiElement) {
    if (psiElement.getLanguage() == JavaLanguage.INSTANCE && !PsiUtil.isLanguageLevel5OrHigher(psiElement)) {
      return ThreeState.NO;
    }
    if (UastContextKt.getUastParentOfType(psiElement, UAnnotation.class) != null) {
      return ThreeState.YES;
    }
    if (UastContextKt.getUastParentOfType(psiElement, UImportStatement.class) != null) {
      return ThreeState.UNSURE;
    }
    return ThreeState.NO;
  }

  public static void importClass(@NotNull Module currentModule, @Nullable Editor editor, @Nullable PsiReference reference, @Nullable String className) {
    Project project = currentModule.getProject();
    if (editor != null && reference != null && className != null) {
      DumbService.getInstance(project).withAlternativeResolveEnabled(() -> {
        GlobalSearchScope scope = GlobalSearchScope.moduleWithLibrariesScope(currentModule);
        PsiClass aClass = JavaPsiFacade.getInstance(project).findClass(className, scope);
        if (aClass != null) {
          new AddImportAction(project, reference, editor, aClass).execute();
        }
      });
    }
  }

  public static void addJarToRoots(@NotNull String jarPath, final @NotNull Module module, @Nullable PsiElement location) {
    addJarsToRoots(Collections.singletonList(jarPath), null, module, location);
  }

  public static void addJarsToRoots(@NotNull List<String> jarPaths, @Nullable String libraryName, @NotNull Module module, @Nullable PsiElement location) {
    List<String> urls = refreshAndConvertToUrls(jarPaths);
    DependencyScope scope = suggestScopeByLocation(module, location);
    ModuleRootModificationUtil.addModuleLibrary(module, libraryName, urls, Collections.emptyList(), scope);
  }

  @NotNull
  public static List<String> refreshAndConvertToUrls(@NotNull List<String> jarPaths) {
    return ContainerUtil.map(jarPaths, OrderEntryFix::refreshAndConvertToUrl);
  }

  @NotNull
  public static DependencyScope suggestScopeByLocation(@NotNull Module module, @Nullable PsiElement location) {
    if (location != null) {
      final VirtualFile vFile = location.getContainingFile().getVirtualFile();
      if (vFile != null && ModuleRootManager.getInstance(module).getFileIndex().isInTestSourceContent(vFile)) {
        return DependencyScope.TEST;
      }
    }
    return DependencyScope.COMPILE;
  }

  @NotNull
  private static String refreshAndConvertToUrl(String jarPath) {
    final File libraryRoot = new File(jarPath);
    LocalFileSystem.getInstance().refreshAndFindFileByIoFile(libraryRoot);
    return VfsUtil.getUrlForLibraryRoot(libraryRoot);
  }
}
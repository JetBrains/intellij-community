// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.quickFix.ExternalLibraryResolver;
import com.intellij.codeInsight.daemon.quickFix.ExternalLibraryResolver.ExternalClassResolveResult;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.MoveToTestRootFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightJavaModule;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Function;
import com.intellij.util.SmartList;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.*;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class OrderEntryFix implements IntentionAction, LocalQuickFix {
  private final SmartPsiFileRange myReferencePointer;

  protected OrderEntryFix(@NotNull PsiReference reference) {
    myReferencePointer = createReferencePointer(reference);
  }

  private static @Nullable SmartPsiFileRange createReferencePointer(@NotNull PsiReference reference) {
    PsiElement element = reference.getElement();
    int offset = element.getTextRange().getStartOffset() + reference.getRangeInElement().getStartOffset();
    PsiFile file = element.getContainingFile();
    if (areReferencesEquivalent(reference, file.findReferenceAt(offset))) {
      return SmartPointerManager.getInstance(element.getProject()).createSmartPsiFileRangePointer(file, TextRange.from(offset, 0));
    }
    return null;
  }

  private static boolean areReferencesEquivalent(@NotNull PsiReference ref1, @Nullable PsiReference ref2) {
    if (ref2 == null) return false;
    if (ref1.getClass() != ref2.getClass()) return false;
    if (ref1.getElement() != ref2.getElement()) return false;
    return ref1.getRangeInElement().equals(ref2.getRangeInElement());
  }

  @Nullable PsiReference restoreReference() {
    PsiFile file = myReferencePointer == null ? null : myReferencePointer.getContainingFile();
    Segment range = myReferencePointer == null ? null : myReferencePointer.getPsiRange();
    return file == null || range == null ? null : file.findReferenceAt(range.getStartOffset());
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public @NotNull String getName() {
    return getText();
  }

  @Override
  public void applyFix(final @NotNull Project project, final @NotNull ProblemDescriptor descriptor) {
    invoke(project, null, descriptor.getPsiElement().getContainingFile());
  }

  public static @NotNull List<@NotNull LocalQuickFix> registerFixes(@NotNull PsiReference reference, @NotNull List<? super IntentionAction> registrar) {
    return registerFixes(reference, registrar, shortReferenceName -> {
      Project project = reference.getElement().getProject();
      return PsiShortNamesCache.getInstance(project).getClassesByName(shortReferenceName, GlobalSearchScope.allScope(project));
    });
  }

  public static @NotNull List<@NotNull LocalQuickFix> registerFixes(@NotNull PsiReference reference,
                                                                    @NotNull List<? super IntentionAction> registrar,
                                                                    @NotNull Function<? super String, PsiClass[]> shortReferenceNameToClassesLookup) {
    PsiElement psiElement = reference.getElement();
    String shortReferenceName = reference.getRangeInElement().substring(psiElement.getText());

    Project project = psiElement.getProject();
    PsiFile containingFile = psiElement.getContainingFile();
    if (containingFile == null) return Collections.emptyList();
    VirtualFile refVFile = containingFile.getVirtualFile();
    if (refVFile == null) return Collections.emptyList();

    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    Module currentModule = fileIndex.getModuleForFile(refVFile);
    if (currentModule == null) return Collections.emptyList();

    DependencyScope scope = fileIndex.isInTestSourceContent(refVFile) ? DependencyScope.TEST : DependencyScope.COMPILE;

    if (reference instanceof PsiJavaModuleReference moduleReference) {
      List<LocalQuickFix> result = new SmartList<>();
      createModuleFixes(moduleReference, currentModule, scope, result);
      result.forEach(fix -> registrar.add((IntentionAction)fix));
      return result;
    }

    List<LocalQuickFix> result = new SmartList<>();
    JavaPsiFacade facade = JavaPsiFacade.getInstance(project);

    registerExternalFixes(reference, psiElement, shortReferenceName, currentModule, scope, registrar, result);
    if (!result.isEmpty()) {
      return result;
    }

    PsiClass[] classes = shortReferenceNameToClassesLookup.fun(shortReferenceName);
    List<PsiClass> allowedDependencies = filterAllowedDependencies(psiElement, classes);
    if (allowedDependencies.isEmpty()) {
      return result;
    }
    if (reference instanceof PsiJavaCodeReferenceElement codeReference) {
      String qualifiedName = getQualifiedName(codeReference, shortReferenceName, containingFile);
      if (qualifiedName != null) {
        allowedDependencies.removeIf(aClass -> !qualifiedName.equals(aClass.getQualifiedName()));
      }
    }

    OrderEntryFix moduleDependencyFix = new AddModuleDependencyFix(reference, currentModule, scope, allowedDependencies);
    registrar.add(moduleDependencyFix);
    result.add(moduleDependencyFix);

    Map<Library, String> librariesToAdd = new HashMap<>();
    Set<VirtualFile> jars = new HashSet<>();
    Set<Library> excluded = new HashSet<>();
    Set<Library> withTestScope = new HashSet<>();
    ModuleFileIndex moduleFileIndex = ModuleRootManager.getInstance(currentModule).getFileIndex();
    for (PsiClass aClass : allowedDependencies) {
      if (!facade.getResolveHelper().isAccessible(aClass, psiElement, aClass)) continue;
      PsiFile psiFile = aClass.getContainingFile();
      if (psiFile == null) continue;
      VirtualFile virtualFile = psiFile.getVirtualFile();
      if (virtualFile == null) continue;
      for (OrderEntry orderEntry : fileIndex.getOrderEntriesForFile(virtualFile)) {
        if (orderEntry instanceof LibraryOrderEntry libraryEntry) {
          final Library library = libraryEntry.getLibrary();
          if (library == null) continue;
          VirtualFile[] files = library.getFiles(OrderRootType.CLASSES);
          if (files.length == 0) continue;
          final VirtualFile jar = files[0];

          String qualifiedName = aClass.getQualifiedName();
          if (qualifiedName == null) continue;

          if (jar == null || 
              libraryEntry.isModuleLevel() && !jars.add(jar) || 
              librariesToAdd.putIfAbsent(library, qualifiedName) != null) {
            continue;
          }
          OrderEntry entryForFile = moduleFileIndex.getOrderEntryForFile(virtualFile);
          if (entryForFile != null) {
            boolean testScopeLibraryInProduction = entryForFile instanceof ExportableOrderEntry exportableOrderEntry &&
                                                   exportableOrderEntry.getScope() == DependencyScope.TEST &&
                                                   !moduleFileIndex.isInTestSourceContent(refVFile);
            if (testScopeLibraryInProduction) {
              withTestScope.add(library);
            }
            else {
              excluded.add(library);
            }
          }
        }
      }

      excluded.forEach(librariesToAdd::remove);
      
      if (!librariesToAdd.isEmpty()) {
        class AddLibraryFix extends AddLibraryDependencyFix implements PriorityAction {
          private AddLibraryFix(@NotNull PsiReference reference,
                                @NotNull Module currentModule,
                                @NotNull Map<Library, String> libraries,
                                @NotNull DependencyScope scope, boolean exported) {
            super(reference, currentModule, libraries, scope, exported);
          }
          @Override
          public @NotNull Priority getPriority() {
            return withTestScope.isEmpty() ? Priority.NORMAL : Priority.LOW;
          }
        }
        OrderEntryFix fix = new AddLibraryFix(reference, currentModule, librariesToAdd, scope, false);
        registrar.add(fix);
        result.add(fix);
      }
      
      if (!withTestScope.isEmpty()) {
        MoveToTestRootFix fix = new MoveToTestRootFix(containingFile);
        if (fix.isAvailable(containingFile)) {
          registrar.add(fix);
          result.add(fix);
        }
      }
      
    }

    return result;
  }

  private static @Nullable String getQualifiedName(@NotNull PsiJavaCodeReferenceElement reference,
                                                   String shortReferenceName,
                                                   PsiFile containingFile) {
    String qualifiedName = null;
    if (reference.isQualified()) {
      qualifiedName = reference.getQualifiedName();
    }
    else if (containingFile instanceof PsiJavaFile psiJavaFile) {
      PsiImportList list = psiJavaFile.getImportList();
      if (list != null) {
        PsiImportStatementBase statement = list.findSingleImportStatement(shortReferenceName);
        if (statement != null) {
          //noinspection ConstantConditions
          qualifiedName = statement.getImportReference().getQualifiedName();
        }
      }
    }
    return qualifiedName;
  }

  private static void createModuleFixes(@NotNull PsiPolyVariantReference reference,
                                        @NotNull Module currentModule,
                                        @NotNull DependencyScope scope,
                                        @NotNull List<? super @NotNull LocalQuickFix> result) {
    ProjectFileIndex index = ProjectRootManager.getInstance(currentModule.getProject()).getFileIndex();
    List<PsiElement> targets = Stream.of(reference.multiResolve(true))
      .map(ResolveResult::getElement)
      .filter(Objects::nonNull).toList();

    PsiElement statement = reference.getElement().getParent();
    boolean exported = statement instanceof PsiRequiresStatement requires && requires.hasModifierProperty(PsiModifier.TRANSITIVE);

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

    Set<Library> lightLibraries = targets.stream()
      .map(e -> e instanceof LightJavaModule light ? light.getRootVirtualFile() : null)
      .flatMap(vf -> vf != null ? index.getOrderEntriesForFile(vf).stream() : Stream.empty())
      .map(e -> e instanceof LibraryOrderEntry lib ? lib.getLibrary() : null)
      .filter(Objects::nonNull)
      .collect(Collectors.toSet());
    if (!lightLibraries.isEmpty()) {
      result.add(new AddLibraryDependencyFix(reference, currentModule,
                                             ContainerUtil.map2Map(lightLibraries, library -> Pair.create(library, "")), scope, exported));
    }

    Set<Library> clsLibraries = targets.stream()
      .map(e -> e instanceof PsiCompiledElement ? e.getContainingFile() : null)
      .map(f -> f != null ? f.getVirtualFile() : null)
      .filter(vf -> vf != null && !index.isInSource(vf))
      .flatMap(vf -> index.getOrderEntriesForFile(vf).stream())
      .map(e -> e instanceof LibraryOrderEntry lib ? lib.getLibrary() : null)
      .filter(Objects::nonNull)
      .collect(Collectors.toSet());
    if (!clsLibraries.isEmpty()) {
      result.add(new AddLibraryDependencyFix(reference, currentModule,
                                             ContainerUtil.map2Map(clsLibraries, library -> Pair.create(library, "")), scope, exported));
    }
  }

  private static void registerExternalFixes(@NotNull PsiReference reference,
                                            @NotNull PsiElement psiElement,
                                            @NotNull String shortReferenceName,
                                            @NotNull Module currentModule,
                                            @NotNull DependencyScope scope,
                                            @NotNull List<? super @NotNull IntentionAction> registrar,
                                            @NotNull List<? super @NotNull LocalQuickFix> result) {
    JavaPsiFacade facade = JavaPsiFacade.getInstance(currentModule.getProject());
    String fullReferenceText = reference.getCanonicalText();
    ThreeState refToAnnotation = isReferenceToAnnotation(psiElement);
    for (ExternalLibraryResolver resolver : ExternalLibraryResolver.EP_NAME.getExtensionList()) {
      ExternalClassResolveResult resolveResult = resolver.resolveClass(shortReferenceName, refToAnnotation, currentModule);
      OrderEntryFix fix = null;
      if (resolveResult != null &&
          facade.findClass(resolveResult.getQualifiedClassName(), currentModule.getModuleWithDependenciesAndLibrariesScope(true)) == null) {
        final ExternalLibraryDescriptor descriptor = resolveResult.getLibraryDescriptor();
        final DependencyScope useScope = Objects.requireNonNullElse(descriptor.getPreferredScope(), scope);
        fix = new AddExtLibraryDependencyFix(reference, currentModule, descriptor, useScope, resolveResult.getQualifiedClassName());
      }
      else if (!fullReferenceText.equals(shortReferenceName) &&
               facade.findClass(fullReferenceText, currentModule.getModuleWithDependenciesAndLibrariesScope(true)) == null) {
        ExternalLibraryDescriptor descriptor = resolver.resolvePackage(fullReferenceText);
        if (descriptor != null) {
          final DependencyScope useScope = Objects.requireNonNullElse(descriptor.getPreferredScope(), scope);
          fix = new AddExtLibraryDependencyFix(reference, currentModule, descriptor, useScope, null);
        }
      }
      if (fix != null) {
        registrar.add(fix);
        result.add(fix);
      }
    }
  }

  private static @NotNull List<PsiClass> filterAllowedDependencies(@NotNull PsiElement element, PsiClass @NotNull [] classes) {
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

  private static @NotNull ThreeState isReferenceToAnnotation(final @NotNull PsiElement psiElement) {
    if (psiElement.getLanguage() == JavaLanguage.INSTANCE && !PsiUtil.isAvailable(JavaFeature.ANNOTATIONS, psiElement)) {
      return ThreeState.NO;
    }
    UElement uElement = UastContextKt.toUElement(psiElement);
    while (uElement != null) {
      if (uElement instanceof UAnnotation) {
        return ThreeState.YES;
      }
      if (uElement instanceof UImportStatement) {
        return ThreeState.UNSURE;  
      }
      if (uElement instanceof UDeclaration || uElement instanceof UFile) {
        break;
      }
      uElement = uElement.getUastParent();
    }
    return ThreeState.NO;
  }

  public static void addJarToRoots(@NotNull String jarPath, final @NotNull Module module, @Nullable PsiElement location) {
    addJarsToRoots(Collections.singletonList(jarPath), null, module, location);
  }

  public static void addJarsToRoots(@NotNull List<String> jarPaths, @Nullable String libraryName, @NotNull Module module, @Nullable PsiElement location) {
    List<String> urls = refreshAndConvertToUrls(jarPaths);
    DependencyScope scope = suggestScopeByLocation(module, location);
    ModuleRootModificationUtil.addModuleLibrary(module, libraryName, urls, Collections.emptyList(), scope);
  }

  public static @NotNull List<String> refreshAndConvertToUrls(@NotNull List<String> jarPaths) {
    return ContainerUtil.map(jarPaths, OrderEntryFix::refreshAndConvertToUrl);
  }

  public static @NotNull DependencyScope suggestScopeByLocation(@NotNull Module module, @Nullable PsiElement location) {
    if (location != null) {
      final VirtualFile vFile = location.getContainingFile().getVirtualFile();
      if (vFile != null && ModuleRootManager.getInstance(module).getFileIndex().isInTestSourceContent(vFile)) {
        return DependencyScope.TEST;
      }
    }
    return DependencyScope.COMPILE;
  }

  private static @NotNull String refreshAndConvertToUrl(String jarPath) {
    final File libraryRoot = new File(jarPath);
    LocalFileSystem.getInstance().refreshAndFindFileByIoFile(libraryRoot);
    return VfsUtil.getUrlForLibraryRoot(libraryRoot);
  }
}
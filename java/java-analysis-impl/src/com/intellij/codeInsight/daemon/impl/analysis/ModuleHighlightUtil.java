/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.quickfix.*;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightJavaModule;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.intellij.openapi.util.Pair.pair;
import static com.intellij.psi.PsiJavaModule.MODULE_INFO_FILE;
import static com.intellij.psi.SyntaxTraverser.psiTraverser;

public class ModuleHighlightUtil {
  @Nullable
  static PsiJavaModule getModuleDescriptor(@NotNull PsiFileSystemItem fsItem) {
    VirtualFile file = fsItem.getVirtualFile();
    if (file == null) return null;

    Project project = fsItem.getProject();
    ProjectFileIndex index = ProjectFileIndex.SERVICE.getInstance(project);
    if (index.isInLibraryClasses(file)) {
      VirtualFile classRoot = index.getClassRootForFile(file);
      if (classRoot != null) {
        VirtualFile descriptorFile = classRoot.findChild(PsiJavaModule.MODULE_INFO_CLS_FILE);
        if (descriptorFile == null) {
          descriptorFile = classRoot.findFileByRelativePath("META-INF/versions/9/" + PsiJavaModule.MODULE_INFO_CLS_FILE);
        }
        if (descriptorFile != null) {
          PsiFile psiFile = PsiManager.getInstance(project).findFile(descriptorFile);
          if (psiFile instanceof PsiJavaFile) {
            return ((PsiJavaFile)psiFile).getModuleDeclaration();
          }
        }
        else if (classRoot.getFileSystem() instanceof JarFileSystem && "jar".equalsIgnoreCase(classRoot.getExtension())) {
          return LightJavaModule.getModule(PsiManager.getInstance(project), classRoot);
        }
      }

      return null;
    }
    else {
      return getModuleDescriptor(index.getModuleForFile(file));
    }
  }

  static PsiJavaModule getModuleDescriptor(Module module) {
    return Optional.ofNullable(module)
      .map(m -> FilenameIndex.getVirtualFilesByName(module.getProject(), MODULE_INFO_FILE, m.getModuleScope(false)))
      .map(c -> c.size() == 1 ? c.iterator().next() : null)
      .map(f -> PsiManager.getInstance(module.getProject()).findFile(f))
      .map(f -> f instanceof PsiJavaFile ? ((PsiJavaFile)f).getModuleDeclaration() : null)
      .orElse(null);
  }

  @Nullable
  static HighlightInfo checkFileName(@NotNull PsiJavaModule element, @NotNull PsiFile file) {
    if (!MODULE_INFO_FILE.equals(file.getName())) {
      String message = JavaErrorMessages.message("module.file.wrong.name");
      HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(range(element)).description(message).create();
      QuickFixAction.registerQuickFixAction(info, factory().createRenameFileFix(MODULE_INFO_FILE));
      return info;
    }

    return null;
  }

  @Nullable
  static HighlightInfo checkFileDuplicates(@NotNull PsiJavaModule element, @NotNull PsiFile file) {
    Module module = findModule(file);
    if (module != null) {
      Project project = file.getProject();
      Collection<VirtualFile> others = FilenameIndex.getVirtualFilesByName(project, MODULE_INFO_FILE, module.getModuleScope(false));
      if (others.size() > 1) {
        String message = JavaErrorMessages.message("module.file.duplicate");
        HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(range(element)).description(message).create();
        others.stream().map(f -> PsiManager.getInstance(project).findFile(f)).filter(f -> f != file).findFirst().ifPresent(
          duplicate -> QuickFixAction.registerQuickFixAction(info, new GoToSymbolFix(duplicate, JavaErrorMessages.message("module.open.duplicate.text")))
        );
        return info;
      }
    }

    return null;
  }

  @NotNull
  static List<HighlightInfo> checkDuplicateStatements(@NotNull PsiJavaModule module) {
    List<HighlightInfo> results = ContainerUtil.newSmartList();

    checkDuplicateRefs(
      module.getRequires(),
      st -> Optional.ofNullable(st.getReferenceElement()).map(PsiJavaModuleReferenceElement::getReferenceText),
      "module.duplicate.requires", results);

    checkDuplicateRefs(
      module.getExports(),
      st -> Optional.ofNullable(st.getPackageReference()).map(ModuleHighlightUtil::refText),
      "module.duplicate.export", results);

    checkDuplicateRefs(
      psiTraverser().children(module).filter(PsiUsesStatement.class),
      st -> Optional.ofNullable(st.getClassReference()).map(ModuleHighlightUtil::refText),
      "module.duplicate.uses", results);

    checkDuplicateRefs(
      psiTraverser().children(module).filter(PsiProvidesStatement.class),
      st -> Optional.of(pair(st.getInterfaceReference(), st.getImplementationReference()))
        .map(p -> p.first != null && p.second != null ? refText(p.first) + " / " + refText(p.second) : null),
      "module.duplicate.provides", results);

    return results;
  }

  private static <T extends PsiElement> void checkDuplicateRefs(Iterable<T> statements,
                                                                Function<T, Optional<String>> ref,
                                                                @PropertyKey(resourceBundle = JavaErrorMessages.BUNDLE) String key,
                                                                List<HighlightInfo> results) {
    Set<String> filter = ContainerUtil.newTroveSet();
    for (T statement : statements) {
      String refText = ref.apply(statement).orElse(null);
      if (refText != null && !filter.add(refText)) {
        String message = JavaErrorMessages.message(key, refText);
        HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(statement).description(message).create();
        QuickFixAction.registerQuickFixAction(info, new DeleteElementFix(statement));
        results.add(info);
      }
    }
  }

  @NotNull
  static List<HighlightInfo> checkUnusedServices(@NotNull PsiJavaModule module) {
    List<HighlightInfo> results = ContainerUtil.newSmartList();

    Set<String> exports = ContainerUtil.newTroveSet(), uses = ContainerUtil.newTroveSet();
    for (PsiElement child : psiTraverser().children(module)) {
      if (child instanceof PsiExportsStatement) {
        PsiJavaCodeReferenceElement ref = ((PsiExportsStatement)child).getPackageReference();
        if (ref != null) exports.add(refText(ref));
      }
      else if (child instanceof PsiUsesStatement) {
        PsiJavaCodeReferenceElement ref = ((PsiUsesStatement)child).getClassReference();
        if (ref != null) uses.add(refText(ref));
      }
    }

    Module host = findModule(module);
    for (PsiProvidesStatement statement : psiTraverser().children(module).filter(PsiProvidesStatement.class)) {
      PsiJavaCodeReferenceElement ref = statement.getInterfaceReference();
      if (ref != null) {
        PsiElement target = ref.resolve();
        if (target instanceof PsiClass && findModule(target) == host) {
          String className = refText(ref), packageName = StringUtil.getPackageName(className);
          if (!exports.contains(packageName) && !uses.contains(className)) {
            String message = JavaErrorMessages.message("module.service.unused");
            results.add(HighlightInfo.newHighlightInfo(HighlightInfoType.WARNING).range(range(ref)).description(message).create());
          }
        }
      }
    }

    return results;
  }

  private static String refText(PsiJavaCodeReferenceElement ref) {
    return PsiNameHelper.getQualifiedClassName(ref.getText(), true);
  }

  @Nullable
  static HighlightInfo checkFileLocation(@NotNull PsiJavaModule element, @NotNull PsiFile file) {
    VirtualFile vFile = file.getVirtualFile();
    if (vFile != null) {
      VirtualFile root = ProjectFileIndex.SERVICE.getInstance(file.getProject()).getSourceRootForFile(vFile);
      if (root != null && !root.equals(vFile.getParent())) {
        String message = JavaErrorMessages.message("module.file.wrong.location");
        HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.WARNING).range(range(element)).description(message).create();
        QuickFixAction.registerQuickFixAction(info, new MoveFileFix(vFile, root, QuickFixBundle.message("move.file.to.source.root.text")));
        return info;
      }
    }

    return null;
  }

  @Nullable
  static HighlightInfo checkModuleReference(@Nullable PsiJavaModuleReferenceElement refElement, @NotNull PsiJavaModule container) {
    if (refElement != null) {
      PsiPolyVariantReference ref = refElement.getReference();
      assert ref != null : refElement.getParent();
      PsiElement target = ref.resolve();
      if (!(target instanceof PsiJavaModule)) {
        return moduleResolveError(refElement, ref);
      }
      else if (target == container) {
        String message = JavaErrorMessages.message("module.cyclic.dependence", container.getModuleName());
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(refElement).description(message).create();
      }
      else {
        Collection<PsiJavaModule> cycle = JavaModuleGraphUtil.findCycle((PsiJavaModule)target);
        if (cycle != null && cycle.contains(container)) {
          Stream<String> stream = cycle.stream().map(PsiJavaModule::getModuleName);
          if (ApplicationManager.getApplication().isUnitTestMode()) stream = stream.sorted();
          String message = JavaErrorMessages.message("module.cyclic.dependence", stream.collect(Collectors.joining(", ")));
          return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(refElement).description(message).create();
        }
      }
    }

    return null;
  }

  @Nullable
  static HighlightInfo checkPackageReference(@Nullable PsiJavaCodeReferenceElement refElement) {
    if (refElement != null) {
      PsiElement target = refElement.resolve();
      if (target instanceof PsiPackage) {
        Module module = findModule(refElement);
        if (module != null) {
          String packageName = ((PsiPackage)target).getQualifiedName();
          PsiDirectory[] directories = ((PsiPackage)target).getDirectories(module.getModuleScope(false));
          if (directories.length == 0) {
            String message = JavaErrorMessages.message("package.not.found", packageName);
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(refElement).description(message).create();
          }
          if (PsiUtil.isPackageEmpty(directories, packageName)) {
            String message = JavaErrorMessages.message("package.is.empty", packageName);
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(refElement).description(message).create();
          }
        }
      }
    }

    return null;
  }

  @NotNull
  static List<HighlightInfo> checkExportTargets(@NotNull PsiExportsStatement statement, @NotNull PsiJavaModule container) {
    List<HighlightInfo> results = ContainerUtil.newSmartList();

    Set<String> targets = ContainerUtil.newTroveSet();
    for (PsiJavaModuleReferenceElement refElement : statement.getModuleReferences()) {
      String refText = refElement.getReferenceText();
      PsiPolyVariantReference ref = refElement.getReference();
      assert ref != null : statement;
      PsiElement target = ref.resolve();
      if (!(target instanceof PsiJavaModule)) {
        results.add(moduleResolveError(refElement, ref));
      }
      else if (!targets.add(refText)) {
        String message = JavaErrorMessages.message("module.duplicate.export", refText);
        results.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(refElement).description(message).create());
      }
    }

    return results;
  }

  @Nullable
  static HighlightInfo checkServiceReference(@Nullable PsiJavaCodeReferenceElement refElement) {
    if (refElement != null) {
      PsiElement target = refElement.resolve();
      if (target instanceof PsiClass && ((PsiClass)target).isEnum()) {
        String message = JavaErrorMessages.message("module.service.enum", ((PsiClass)target).getName());
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(range(refElement)).description(message).create();
      }
    }

    return null;
  }

  @Nullable
  static HighlightInfo checkServiceImplementation(@Nullable PsiJavaCodeReferenceElement implRef,
                                                  @Nullable PsiJavaCodeReferenceElement intRef) {
    if (implRef != null && intRef != null) {
      PsiElement implTarget = implRef.resolve(), intTarget = intRef.resolve();
      if (implTarget instanceof PsiClass && intTarget instanceof PsiClass) {
        PsiClass implClass = (PsiClass)implTarget;
        if (!InheritanceUtil.isInheritorOrSelf(implClass, (PsiClass)intTarget, true)) {
          String message = JavaErrorMessages.message("module.service.subtype");
          return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(range(implRef)).description(message).create();
        }
        if (implClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
          String message = JavaErrorMessages.message("module.service.abstract", implClass.getName());
          return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(range(implRef)).description(message).create();
        }

        PsiMethod[] constructors = implClass.getConstructors();
        if (constructors.length > 0) {
          PsiMethod constructor = JBIterable.of(constructors).find(c -> c.getParameterList().getParametersCount() == 0);
          if (constructor == null) {
            String message = JavaErrorMessages.message("module.service.no.ctor", implClass.getName());
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(range(implRef)).description(message).create();
          }
          if (!constructor.hasModifierProperty(PsiModifier.PUBLIC)) {
            String message = JavaErrorMessages.message("module.service.hidden.ctor", implClass.getName());
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(range(implRef)).description(message).create();
          }
        }
      }
    }

    return null;
  }

  @Nullable
  static HighlightInfo checkPackageAccessibility(@NotNull PsiJavaCodeReferenceElement ref,
                                                 @NotNull PsiElement target,
                                                 @NotNull PsiJavaModule refModule) {
    Module module = findModule(refModule);
    if (module != null) {
      if (target instanceof PsiClass) {
        PsiElement targetFile = target.getParent();
        if (targetFile instanceof PsiClassOwner) {
          PsiJavaModule targetModule = getModuleDescriptor((PsiFileSystemItem)targetFile);
          String packageName = ((PsiClassOwner)targetFile).getPackageName();
          return checkPackageAccessibility(ref, refModule, targetModule, packageName);
        }
      }
      else if (target instanceof PsiPackage) {
        PsiElement refImport = ref.getParent();
        if (refImport instanceof PsiImportStatementBase && ((PsiImportStatementBase)refImport).isOnDemand()) {
          PsiDirectory[] dirs = ((PsiPackage)target).getDirectories(module.getModuleWithDependenciesAndLibrariesScope(false));
          if (dirs.length == 1) {
            PsiJavaModule targetModule = getModuleDescriptor(dirs[0]);
            String packageName = ((PsiPackage)target).getQualifiedName();
            return checkPackageAccessibility(ref, refModule, targetModule, packageName);
          }
        }
      }
    }

    return null;
  }

  private static HighlightInfo checkPackageAccessibility(PsiJavaCodeReferenceElement ref,
                                                         PsiJavaModule refModule,
                                                         PsiJavaModule targetModule,
                                                         String packageName) {
    if (!refModule.equals(targetModule)) {
      if (targetModule == null) {
        String message = JavaErrorMessages.message("module.package.on.classpath");
        return HighlightInfo.newHighlightInfo(HighlightInfoType.WRONG_REF).range(ref).description(message).create();
      }

      String refModuleName = refModule.getModuleName();
      String requiredName = targetModule.getModuleName();
      if (!(targetModule instanceof LightJavaModule || JavaModuleGraphUtil.exports(targetModule, packageName, refModule))) {
        String message = JavaErrorMessages.message("module.package.not.exported", requiredName, packageName, refModuleName);
        return HighlightInfo.newHighlightInfo(HighlightInfoType.WRONG_REF).range(ref).description(message).create();
      }

      if (!(PsiJavaModule.JAVA_BASE.equals(requiredName) || JavaModuleGraphUtil.reads(refModule, targetModule))) {
        String message = JavaErrorMessages.message("module.not.in.requirements", refModuleName, requiredName);
        HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.WRONG_REF).range(ref).description(message).create();
        QuickFixAction.registerQuickFixAction(info, new AddRequiredModuleFix(refModule, requiredName));
        return info;
      }
    }

    return null;
  }

  private static Module findModule(PsiElement element) {
    return Optional.ofNullable(element.getContainingFile())
      .map(PsiFile::getVirtualFile)
      .map(f -> ModuleUtilCore.findModuleForFile(f, element.getProject()))
      .orElse(null);
  }

  private static HighlightInfo moduleResolveError(PsiJavaModuleReferenceElement refElement, PsiPolyVariantReference ref) {
    if (ref.multiResolve(true).length == 0) {
      String message = JavaErrorMessages.message("module.not.found", refElement.getReferenceText());
      return HighlightInfo.newHighlightInfo(HighlightInfoType.WRONG_REF).range(refElement).description(message).create();
    }
    else if (ref.multiResolve(false).length > 1) {
      String message = JavaErrorMessages.message("module.ambiguous", refElement.getReferenceText());
      return HighlightInfo.newHighlightInfo(HighlightInfoType.WARNING).range(refElement).description(message).create();
    }
    else {
      String message = JavaErrorMessages.message("module.not.on.path", refElement.getReferenceText());
      HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.WRONG_REF).range(refElement).description(message).create();
      factory().registerOrderEntryFixes(new QuickFixActionRegistrarImpl(info), ref);
      return info;
    }
  }

  private static QuickFixFactory factory() {
    return QuickFixFactory.getInstance();
  }

  private static TextRange range(PsiJavaModule module) {
    PsiKeyword kw = PsiTreeUtil.getChildOfType(module, PsiKeyword.class);
    return new TextRange(kw != null ? kw.getTextOffset() : module.getTextOffset(), module.getNameElement().getTextRange().getEndOffset());
  }

  private static PsiElement range(PsiJavaCodeReferenceElement refElement) {
    return ObjectUtils.notNull(refElement.getReferenceNameElement(), refElement);
  }
}
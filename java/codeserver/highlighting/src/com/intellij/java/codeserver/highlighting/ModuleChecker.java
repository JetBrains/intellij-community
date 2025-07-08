// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeserver.highlighting;

import com.intellij.java.codeserver.core.JavaPsiModuleUtil;
import com.intellij.java.codeserver.core.JavaServiceProviderUtil;
import com.intellij.java.codeserver.core.JpmsModuleAccessInfo;
import com.intellij.java.codeserver.core.JpmsModuleAccessInfo.JpmsModuleAccessProblem;
import com.intellij.java.codeserver.core.JpmsModuleInfo;
import com.intellij.java.codeserver.highlighting.errors.JavaCompilationError;
import com.intellij.java.codeserver.highlighting.errors.JavaErrorKind;
import com.intellij.java.codeserver.highlighting.errors.JavaErrorKinds;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.util.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static com.intellij.psi.JavaTokenType.TRANSITIVE_KEYWORD;

final class ModuleChecker {
  private final @NotNull JavaErrorVisitor myVisitor;

  ModuleChecker(@NotNull JavaErrorVisitor visitor) { myVisitor = visitor; }

  void checkPackageStatement(@NotNull PsiPackageStatement statement) {
    PsiFile file = myVisitor.file();
    if (PsiUtil.isModuleFile(file)) {
      myVisitor.report(JavaErrorKinds.MODULE_NO_PACKAGE.create(statement));
      return;
    }

    PsiJavaModule javaModule = myVisitor.javaModule();
    if (javaModule != null) {
      String packageName = statement.getPackageName();
      if (packageName != null) {
        PsiJavaModule origin = JavaPsiModuleUtil.findOrigin(javaModule, packageName);
        if (origin != null) {
          PsiJavaCodeReferenceElement reference = statement.getPackageReference();
          myVisitor.report(JavaErrorKinds.MODULE_CONFLICTING_PACKAGES.create(reference, origin));
        }
      }
    }
    else {
      Module module = ModuleUtilCore.findModuleForFile(file);
      if (module == null) return;
      PsiJavaCodeReferenceElement reference = statement.getPackageReference();
      PsiPackage pack = (PsiPackage)reference.resolve();
      if (pack != null) {
        ProjectFileIndex fileIndex = ProjectRootManager.getInstance(pack.getProject()).getFileIndex();
        for (PsiDirectory directory : pack.getDirectories()) {
          PsiJavaModule anotherJavaModule = JavaPsiModuleUtil.findDescriptorByElement(directory);
          if (anotherJavaModule != null) {
            VirtualFile moduleVFile = PsiUtilCore.getVirtualFile(anotherJavaModule);
            if (moduleVFile != null && ContainerUtil.find(fileIndex.getOrderEntriesForFile(moduleVFile), JdkOrderEntry.class::isInstance) != null) {
              VirtualFile rootForFile = fileIndex.getSourceRootForFile(file.getVirtualFile());
              if (rootForFile != null && JavaCompilerConfigurationProxy.isPatchedModuleRoot(anotherJavaModule.getName(), module, rootForFile)) {
                return;
              }
              for (PsiPackageAccessibilityStatement export : anotherJavaModule.getExports()) {
                String exportPackageName = export.getPackageName();
                if (exportPackageName != null && exportPackageName.equals(pack.getQualifiedName())) {
                  myVisitor.report(JavaErrorKinds.MODULE_CONFLICTING_PACKAGES.create(reference, anotherJavaModule));
                  return;
                }
              }
            }
          }
        }
      }
    }
  }

  void checkFileName(@NotNull PsiJavaModule module) {
    if (!PsiJavaModule.MODULE_INFO_FILE.equals(myVisitor.file().getName())) {
      myVisitor.report(JavaErrorKinds.MODULE_FILE_WRONG_NAME.create(module));
    }
  }

  void checkFileDuplicates(@NotNull PsiJavaModule element) {
    Project project = myVisitor.project();
    PsiFile file = myVisitor.file();
    Module module = ModuleUtilCore.findModuleForFile(file);
    if (module == null) return;
    Collection<VirtualFile> moduleInfos = FilenameIndex.getVirtualFilesByName(PsiJavaModule.MODULE_INFO_FILE, module.getModuleScope());
    JpsModuleSourceRootType<?> type = ProjectFileIndex.getInstance(project).getContainingSourceRootType(file.getVirtualFile());
    if (type == null || !JavaModuleSourceRootTypes.SOURCES.contains(type)) return;
    ModuleFileIndex moduleFileIndex = ModuleRootManager.getInstance(module).getFileIndex();
    Collection<VirtualFile> rootModuleInfos = ContainerUtil.filter(moduleInfos, moduleInfo ->
      moduleFileIndex.isUnderSourceRootOfType(moduleInfo, ContainerUtil.newHashSet(type))
    );
    if (rootModuleInfos.size() > 1) {
      PsiFile duplicate =
        rootModuleInfos.stream().map(f -> PsiManager.getInstance(project).findFile(f)).filter(f -> f != file).findFirst().orElse(null);
      myVisitor.report(JavaErrorKinds.MODULE_FILE_DUPLICATE.create(element, duplicate));
    }
  }

  void checkDuplicateStatements(@NotNull PsiJavaModule module) {
    checkDuplicateRefs(module.getRequires(), st -> st.getModuleName(), JavaErrorKinds.MODULE_DUPLICATE_REQUIRES);
    checkDuplicateRefs(module.getExports(), st -> st.getPackageName(), JavaErrorKinds.MODULE_DUPLICATE_EXPORTS);
    checkDuplicateRefs(module.getOpens(), st -> st.getPackageName(), JavaErrorKinds.MODULE_DUPLICATE_OPENS);
    checkDuplicateRefs(module.getUses(), st -> qName(st.getClassReference()), JavaErrorKinds.MODULE_DUPLICATE_USES);
    checkDuplicateRefs(module.getProvides(), st -> qName(st.getInterfaceReference()), JavaErrorKinds.MODULE_DUPLICATE_PROVIDES);
  }

  void checkModifiers(@NotNull PsiRequiresStatement statement) {
    PsiModifierList modList = statement.getModifierList();
    if (modList != null && PsiJavaModule.JAVA_BASE.equals(statement.getModuleName())) {
      boolean transitiveAvailable = PsiUtil.isAvailable(JavaFeature.TRANSITIVE_DEPENDENCY_ON_JAVA_BASE, statement);
      PsiTreeUtil.processElements(modList, PsiKeyword.class, keyword -> {
        if (keyword.getTokenType() == TRANSITIVE_KEYWORD && transitiveAvailable) return true;
        @SuppressWarnings("MagicConstant") 
        @PsiModifier.ModifierConstant String modifier = keyword.getText();
        myVisitor.report(JavaErrorKinds.MODIFIER_NOT_ALLOWED.create(keyword, modifier));
        return true;
      });
    }
  }

  void checkDuplicateModuleReferences(@NotNull PsiPackageAccessibilityStatement statement) {
    Set<String> targets = new HashSet<>();
    for (PsiJavaModuleReferenceElement refElement : statement.getModuleReferences()) {
      String refText = refElement.getReferenceText();
      PsiJavaModuleReference ref = refElement.getReference();
      assert ref != null : statement;
      if (!targets.add(refText)) {
        boolean exports = statement.getRole() == PsiPackageAccessibilityStatement.Role.EXPORTS;
        var kind = exports ? JavaErrorKinds.MODULE_DUPLICATE_EXPORTS_TARGET : JavaErrorKinds.MODULE_DUPLICATE_OPENS_TARGET;
        myVisitor.report(kind.create(refElement));
      }
    }
  }

  private static String qName(PsiJavaCodeReferenceElement ref) {
    return ref != null ? ref.getQualifiedName() : null;
  }
  
  private <T extends PsiStatement> void checkDuplicateRefs(@NotNull Iterable<? extends T> statements,
                                                           @NotNull Function<? super T, String> ref,
                                                           @NotNull JavaErrorKind.Parameterized<T, String> errorKind) {
    Set<String> filter = new HashSet<>();
    for (T statement : statements) {
      String refText = ref.apply(statement);
      if (refText != null && !filter.add(refText)) {
        myVisitor.report(errorKind.create(statement, refText));
      }
    }
  }

  void checkClashingReads(@NotNull PsiJavaModule module) {
    JavaPsiModuleUtil.ModulePackageConflict conflict = JavaPsiModuleUtil.findConflict(module);
    if (conflict != null) {
      myVisitor.report(JavaErrorKinds.MODULE_CONFLICTING_READS.create(module, conflict));
    }
  }

  void checkFileLocation(@NotNull PsiJavaModule element) {
    VirtualFile vFile = myVisitor.file().getVirtualFile();
    if (vFile != null) {
      VirtualFile root = ProjectFileIndex.getInstance(myVisitor.project()).getSourceRootForFile(vFile);
      if (root != null && !root.equals(vFile.getParent())) {
        myVisitor.report(JavaErrorKinds.MODULE_FILE_WRONG_LOCATION.create(element, root));
      }
    }
  }

  void checkHostModuleStrength(@NotNull PsiPackageAccessibilityStatement statement) {
    if (statement.getRole() == PsiPackageAccessibilityStatement.Role.OPENS &&
        statement.getParent() instanceof PsiJavaModule module &&
        module.hasModifierProperty(PsiModifier.OPEN)) {
      myVisitor.report(JavaErrorKinds.MODULE_OPENS_IN_WEAK_MODULE.create(statement, module));
    }
  }

  void checkServiceReference(@Nullable PsiJavaCodeReferenceElement refElement) {
    if (refElement != null) {
      PsiElement target = refElement.resolve();
      if (!(target instanceof PsiClass psiClass)) {
        myVisitor.report(JavaErrorKinds.REFERENCE_UNRESOLVED.create(refElement));
      }
      else if (psiClass.isEnum()) {
        myVisitor.report(JavaErrorKinds.MODULE_SERVICE_ENUM.create(refElement, psiClass));
      }
    }
  }

  void checkModuleReference(@NotNull PsiRequiresStatement statement) {
    PsiJavaModuleReferenceElement refElement = statement.getReferenceElement();
    if (refElement != null) {
      PsiJavaModuleReference ref = refElement.getReference();
      assert ref != null : refElement.getParent();
      PsiJavaModule target = ref.resolve();
      if (target == null) {
        reportUnresolvedJavaModule(refElement);
        return;
      }
      PsiJavaModule container = (PsiJavaModule)statement.getParent();
      if (target == container) {
        myVisitor.report(JavaErrorKinds.MODULE_CYCLIC_DEPENDENCE.create(refElement, Set.of(container)));
      }
      else {
        Collection<PsiJavaModule> cycle = JavaPsiModuleUtil.findCycle(target);
        if (cycle.contains(container)) {
          myVisitor.report(JavaErrorKinds.MODULE_CYCLIC_DEPENDENCE.create(refElement, cycle));
        }
      }
    }
  }

  private void reportUnresolvedJavaModule(@NotNull PsiJavaModuleReferenceElement refElement) {
    PsiJavaModuleReference ref = refElement.getReference();
    assert ref != null : refElement.getParent();

    ResolveResult[] results = ref.multiResolve(true);
    switch (results.length) {
      case 0 -> myVisitor.report(myVisitor.isIncompleteModel()
                       ? JavaErrorKinds.REFERENCE_PENDING.create(refElement)
                       : JavaErrorKinds.MODULE_NOT_FOUND.create(refElement));
      case 1 -> myVisitor.report(JavaErrorKinds.MODULE_NOT_ON_PATH.create(refElement));
      default -> {
        // ambiguous module is reported as warning
      }
    }
  }

  void checkServiceImplementations(@NotNull PsiProvidesStatement statement) {
    PsiReferenceList implRefList = statement.getImplementationList();
    if (implRefList == null) return;

    PsiJavaCodeReferenceElement intRef = statement.getInterfaceReference();
    PsiElement intTarget = intRef != null ? intRef.resolve() : null;

    Set<String> filter = new HashSet<>();
    for (PsiJavaCodeReferenceElement implRef : implRefList.getReferenceElements()) {
      String refText = implRef.getQualifiedName();
      if (!filter.add(refText)) {
        myVisitor.report(JavaErrorKinds.MODULE_DUPLICATE_IMPLEMENTATION.create(implRef));
        continue;
      }

      if (!(intTarget instanceof PsiClass psiClass)) continue;

      PsiElement implTarget = implRef.resolve();
      if (implTarget instanceof PsiClass implClass) {
        Module fileModule = ModuleUtilCore.findModuleForFile(myVisitor.file());
        Module implModule = ModuleUtilCore.findModuleForFile(implClass.getContainingFile());
        if (fileModule != implModule && !JavaMultiReleaseUtil.areMainAndAdditionalMultiReleaseModules(implModule, fileModule)) {
          myVisitor.report(JavaErrorKinds.MODULE_SERVICE_ALIEN.create(implRef));
        }

        PsiMethod provider = JavaServiceProviderUtil.findServiceProviderMethod(implClass);
        if (provider != null) {
          PsiType type = provider.getReturnType();
          PsiClass typeClass = type instanceof PsiClassType classType ? classType.resolve() : null;
          if (!InheritanceUtil.isInheritorOrSelf(typeClass, psiClass, true)) {
            myVisitor.report(JavaErrorKinds.MODULE_SERVICE_PROVIDER_TYPE.create(implRef, implClass));
          }
        }
        else if (InheritanceUtil.isInheritorOrSelf(implClass, psiClass, true)) {
          if (implClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
            myVisitor.report(JavaErrorKinds.MODULE_SERVICE_ABSTRACT.create(implRef, implClass));
          }
          else if (!(ClassUtil.isTopLevelClass(implClass) || implClass.hasModifierProperty(PsiModifier.STATIC))) {
            myVisitor.report(JavaErrorKinds.MODULE_SERVICE_INNER.create(implRef, implClass));
          }
          else if (!PsiUtil.hasDefaultConstructor(implClass)) {
            myVisitor.report(JavaErrorKinds.MODULE_SERVICE_NO_CONSTRUCTOR.create(implRef, implClass));
          }
        }
        else {
          myVisitor.report(JavaErrorKinds.MODULE_SERVICE_IMPLEMENTATION_TYPE.create(
            implRef, new JavaErrorKinds.SuperclassSubclassContext(psiClass, implClass)));
        }
      }
    }
  }

  void checkPackageReference(@NotNull PsiPackageAccessibilityStatement statement) {
    if (statement.getRole() == PsiPackageAccessibilityStatement.Role.OPENS) return;
    PsiJavaCodeReferenceElement refElement = statement.getPackageReference();
    if (refElement == null) return;
    JavaPsiModuleUtil.PackageReferenceState state = JavaPsiModuleUtil.checkPackageReference(statement);
    if (state == JavaPsiModuleUtil.PackageReferenceState.VALID) return;
    var kind = state == JavaPsiModuleUtil.PackageReferenceState.PACKAGE_NOT_FOUND
               ? JavaErrorKinds.MODULE_REFERENCE_PACKAGE_NOT_FOUND
               : JavaErrorKinds.MODULE_REFERENCE_PACKAGE_EMPTY;
    myVisitor.report(kind.create(statement));
  }
  
  JavaErrorKind.Parameterized<PsiElement, JpmsModuleAccessInfo> accessError(@NotNull JpmsModuleAccessProblem problem) {
    return switch (problem) {
      case FROM_NAMED -> JavaErrorKinds.MODULE_ACCESS_FROM_NAMED;
      case FROM_UNNAMED -> JavaErrorKinds.MODULE_ACCESS_FROM_UNNAMED;
      case TO_UNNAMED -> JavaErrorKinds.MODULE_ACCESS_TO_UNNAMED;
      case PACKAGE_BAD_NAME -> JavaErrorKinds.MODULE_ACCESS_PACKAGE_BAD_NAME;
      case BAD_NAME -> JavaErrorKinds.MODULE_ACCESS_BAD_NAME;
      case PACKAGE_NOT_IN_GRAPH -> JavaErrorKinds.MODULE_ACCESS_PACKAGE_NOT_IN_GRAPH;
      case NOT_IN_GRAPH -> JavaErrorKinds.MODULE_ACCESS_NOT_IN_GRAPH;
      case PACKAGE_DOES_NOT_READ -> JavaErrorKinds.MODULE_ACCESS_PACKAGE_DOES_NOT_READ;
      case DOES_NOT_READ -> JavaErrorKinds.MODULE_ACCESS_DOES_NOT_READ;
      case JPS_DEPENDENCY_PROBLEM -> JavaErrorKinds.MODULE_ACCESS_JPS_DEPENDENCY_PROBLEM;
    };
  }

  void checkModuleReference(@NotNull PsiImportModuleStatement statement) {
    PsiJavaModuleReferenceElement refElement = statement.getModuleReference();
    if (refElement == null) return;
    PsiJavaModuleReference ref = refElement.getReference();
    if (ref == null) return;
    PsiJavaModule target = ref.resolve();
    if (target == null) {
      reportUnresolvedJavaModule(refElement);
      return;
    }
    JpmsModuleAccessInfo moduleAccess = new JpmsModuleInfo.TargetModuleInfoByJavaModule(target, "").accessAt(myVisitor.file().getOriginalFile());
    JpmsModuleAccessProblem problem = moduleAccess.checkModuleAccess(statement);
    if (problem != null) {
      myVisitor.report(accessError(problem).create(statement, moduleAccess));
    }
  }

  private static @NotNull PsiElement findPackagePrefix(@NotNull PsiJavaCodeReferenceElement ref) {
    PsiElement candidate = ref;
    while (candidate instanceof PsiJavaCodeReferenceElement element) {
      if (element.resolve() instanceof PsiPackage) return candidate;
      candidate = element.getQualifier();
    }
    return ref;
  }

  void checkModuleAccess(@NotNull PsiModifierListOwner target, @NotNull PsiElement ref) {
    if (target instanceof PsiClass targetClass && !(target instanceof PsiTypeParameter)) {
      String packageName = PsiUtil.getPackageName(targetClass);
      if (packageName != null) {
        checkAccess(packageName, target.getContainingFile(), ref);
      }
    }
    else if (target instanceof PsiPackage targetPackage) {
      checkAccess(targetPackage.getQualifiedName(), null, ref);
    }
  }

  private void checkAccess(@NotNull String targetPackageName, @Nullable PsiFile targetFile, @NotNull PsiElement place) {
    PsiFile file = myVisitor.file().getOriginalFile();
    List<JpmsModuleInfo.@NotNull TargetModuleInfo> infos = JpmsModuleInfo.findTargetModuleInfos(targetPackageName, targetFile, file);
    if (infos == null) return;
    if (infos.isEmpty()) {
      myVisitor.report(JavaErrorKinds.REFERENCE_PACKAGE_NOT_FOUND.create(place, targetPackageName));
      return;
    }
    JavaCompilationError<PsiElement, JpmsModuleAccessInfo> error = null;
    for (JpmsModuleInfo.TargetModuleInfo info : infos) {
      JpmsModuleAccessInfo moduleAccessInfo = info.accessAt(file);
      JpmsModuleAccessProblem problem = moduleAccessInfo.checkAccess(file, JpmsModuleAccessInfo.JpmsModuleAccessMode.READ);
      if (problem == null) return;
      if (error == null) {
        PsiElement anchor = place instanceof PsiJavaCodeReferenceElement ref ? findPackagePrefix(ref) : place;
        error = accessError(problem).create(anchor, moduleAccessInfo);
      }
    }
    myVisitor.report(error);
  }
}

// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.quickfix.*;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.PsiPackageAccessibilityStatement.Role;
import com.intellij.psi.impl.light.LightJavaModule;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;
import org.jetbrains.jps.model.java.JavaSourceRootType;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.intellij.openapi.module.ModuleUtilCore.findModuleForFile;

public class ModuleHighlightUtil {
  private static final Attributes.Name MULTI_RELEASE = new Attributes.Name("Multi-Release");

  @Nullable
  static PsiJavaModule getModuleDescriptor(@Nullable VirtualFile file, @NotNull Project project) {
    if (file == null) return null;

    ProjectFileIndex index = ProjectFileIndex.SERVICE.getInstance(project);
    if (index.isInLibrary(file)) {
      VirtualFile root;
      if ((root = index.getClassRootForFile(file)) != null) {
        VirtualFile descriptorFile = root.findChild(PsiJavaModule.MODULE_INFO_CLS_FILE);
        if (descriptorFile == null) {
          VirtualFile alt = root.findFileByRelativePath("META-INF/versions/9/" + PsiJavaModule.MODULE_INFO_CLS_FILE);
          if (alt != null && isMultiReleaseJar(root)) {
            descriptorFile = alt;
          }
        }
        if (descriptorFile != null) {
          PsiFile psiFile = PsiManager.getInstance(project).findFile(descriptorFile);
          if (psiFile instanceof PsiJavaFile) {
            return ((PsiJavaFile)psiFile).getModuleDeclaration();
          }
        }
        else if (root.getFileSystem() instanceof JarFileSystem && "jar".equalsIgnoreCase(root.getExtension())) {
          return LightJavaModule.getModule(PsiManager.getInstance(project), root);
        }
      }
      else if ((root = index.getSourceRootForFile(file)) != null) {
        VirtualFile descriptorFile = root.findChild(PsiJavaModule.MODULE_INFO_FILE);
        if (descriptorFile != null) {
          PsiFile psiFile = PsiManager.getInstance(project).findFile(descriptorFile);
          if (psiFile instanceof PsiJavaFile) {
            return ((PsiJavaFile)psiFile).getModuleDeclaration();
          }
        }
      }
    }
    else {
      Module module = index.getModuleForFile(file);
      if (module != null) {
        JavaSourceRootType rootType = index.isInTestSourceContent(file) ? JavaSourceRootType.TEST_SOURCE : JavaSourceRootType.SOURCE;
        List<VirtualFile> files = ModuleRootManager.getInstance(module).getSourceRoots(rootType).stream()
          .map(root -> root.findChild(PsiJavaModule.MODULE_INFO_FILE))
          .filter(Objects::nonNull)
          .collect(Collectors.toList());
        if (files.size() == 1) {
          PsiFile psiFile = PsiManager.getInstance(project).findFile(files.get(0));
          if (psiFile instanceof PsiJavaFile) {
            return ((PsiJavaFile)psiFile).getModuleDeclaration();
          }
        }
      }
    }

    return null;
  }

  private static boolean isMultiReleaseJar(VirtualFile root) {
    if (root.getFileSystem() instanceof JarFileSystem) {
      VirtualFile manifest = root.findFileByRelativePath(JarFile.MANIFEST_NAME);
      if (manifest != null) {
        try (InputStream stream = manifest.getInputStream()) {
          return Boolean.valueOf(new Manifest(stream).getMainAttributes().getValue(MULTI_RELEASE));
        }
        catch (IOException ignored) { }
      }
    }

    return false;
  }

  static HighlightInfo checkPackageStatement(@NotNull PsiPackageStatement statement, @NotNull PsiFile file, @Nullable PsiJavaModule module) {
    if (PsiUtil.isModuleFile(file)) {
      String message = JavaErrorMessages.message("module.no.package");
      HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(statement).descriptionAndTooltip(message).create();
      QuickFixAction.registerQuickFixAction(info, factory().createDeleteFix(statement));
      return info;
    }

    if (module != null) {
      String packageName = statement.getPackageName();
      if (packageName != null) {
        PsiJavaModule origin = JavaModuleGraphUtil.findOrigin(module, packageName);
        if (origin != null) {
          String message = JavaErrorMessages.message("module.conflicting.packages", packageName, origin.getName());
          return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(statement).descriptionAndTooltip(message).create();
        }
      }
    }

    return null;
  }

  @Nullable
  static HighlightInfo checkFileName(@NotNull PsiJavaModule element, @NotNull PsiFile file) {
    if (!PsiJavaModule.MODULE_INFO_FILE.equals(file.getName())) {
      String message = JavaErrorMessages.message("module.file.wrong.name");
      HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(range(element)).descriptionAndTooltip(message).create();
      QuickFixAction.registerQuickFixAction(info, factory().createRenameFileFix(PsiJavaModule.MODULE_INFO_FILE));
      return info;
    }

    return null;
  }

  @Nullable
  static HighlightInfo checkFileDuplicates(@NotNull PsiJavaModule element, @NotNull PsiFile file) {
    Module module = findModuleForFile(file);
    if (module != null) {
      Project project = file.getProject();
      Collection<VirtualFile> others = FilenameIndex.getVirtualFilesByName(project, PsiJavaModule.MODULE_INFO_FILE, module.getModuleScope());
      if (others.size() > 1) {
        String message = JavaErrorMessages.message("module.file.duplicate");
        HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(range(element)).descriptionAndTooltip(message).create();
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
    checkDuplicateRefs(module.getRequires(), st -> st.getModuleName(), "module.duplicate.requires", results);
    checkDuplicateRefs(module.getExports(), st -> st.getPackageName(), "module.duplicate.exports", results);
    checkDuplicateRefs(module.getOpens(), st -> st.getPackageName(), "module.duplicate.opens", results);
    checkDuplicateRefs(module.getUses(), st -> qName(st.getClassReference()), "module.duplicate.uses", results);
    checkDuplicateRefs(module.getProvides(), st -> qName(st.getInterfaceReference()), "module.duplicate.provides", results);
    return results;
  }

  private static <T extends PsiStatement> void checkDuplicateRefs(Iterable<T> statements,
                                                                  Function<T, String> ref,
                                                                  @PropertyKey(resourceBundle = JavaErrorMessages.BUNDLE) String key,
                                                                  List<HighlightInfo> results) {
    Set<String> filter = ContainerUtil.newTroveSet();
    for (T statement : statements) {
      String refText = ref.apply(statement);
      if (refText != null && !filter.add(refText)) {
        String message = JavaErrorMessages.message(key, refText);
        HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(statement).descriptionAndTooltip(message).create();
        QuickFixAction.registerQuickFixAction(info, factory().createDeleteFix(statement));
        QuickFixAction.registerQuickFixAction(info, MergeModuleStatementsFix.createFix(statement));
        results.add(info);
      }
    }
  }

  @NotNull
  static List<HighlightInfo> checkUnusedServices(@NotNull PsiJavaModule module, @NotNull PsiFile file) {
    List<HighlightInfo> results = ContainerUtil.newSmartList();

    Module host = findModuleForFile(file);
    if (host != null) {
      List<PsiProvidesStatement> provides = JBIterable.from(module.getProvides()).toList();
      if (!provides.isEmpty()) {
        Set<String> exports = JBIterable.from(module.getExports()).map(PsiPackageAccessibilityStatement::getPackageName).filter(Objects::nonNull).toSet();
        Set<String> uses = JBIterable.from(module.getUses()).map(st -> qName(st.getClassReference())).filter(Objects::nonNull).toSet();
        for (PsiProvidesStatement statement : provides) {
          PsiJavaCodeReferenceElement ref = statement.getInterfaceReference();
          if (ref != null) {
            PsiElement target = ref.resolve();
            if (target instanceof PsiClass && findModuleForFile(target.getContainingFile()) == host) {
              String className = qName(ref), packageName = StringUtil.getPackageName(className);
              if (!exports.contains(packageName) && !uses.contains(className)) {
                String message = JavaErrorMessages.message("module.service.unused");
                HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.WARNING).range(range(ref)).descriptionAndTooltip(message).create();
                QuickFixAction.registerQuickFixAction(info, new AddExportsDirectiveFix(module, packageName, ""));
                QuickFixAction.registerQuickFixAction(info, new AddUsesDirectiveFix(module, className));
                results.add(info);
              }
            }
          }
        }
      }
    }

    return results;
  }

  private static String qName(PsiJavaCodeReferenceElement ref) {
    return ref != null ? ref.getQualifiedName() : null;
  }

  @Nullable
  static HighlightInfo checkFileLocation(@NotNull PsiJavaModule element, @NotNull PsiFile file) {
    VirtualFile vFile = file.getVirtualFile();
    if (vFile != null) {
      VirtualFile root = ProjectFileIndex.SERVICE.getInstance(file.getProject()).getSourceRootForFile(vFile);
      if (root != null && !root.equals(vFile.getParent())) {
        String message = JavaErrorMessages.message("module.file.wrong.location");
        HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(range(element)).descriptionAndTooltip(message).create();
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
        String message = JavaErrorMessages.message("module.cyclic.dependence", container.getName());
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(refElement).descriptionAndTooltip(message).create();
      }
      else {
        Collection<PsiJavaModule> cycle = JavaModuleGraphUtil.findCycle((PsiJavaModule)target);
        if (cycle != null && cycle.contains(container)) {
          Stream<String> stream = cycle.stream().map(PsiJavaModule::getName);
          if (ApplicationManager.getApplication().isUnitTestMode()) stream = stream.sorted();
          String message = JavaErrorMessages.message("module.cyclic.dependence", stream.collect(Collectors.joining(", ")));
          return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(refElement).descriptionAndTooltip(message).create();
        }
      }
    }

    return null;
  }

  @Nullable
  static HighlightInfo checkHostModuleStrength(@NotNull PsiPackageAccessibilityStatement statement) {
    PsiElement parent;
    if (statement.getRole() == Role.OPENS &&
        (parent = statement.getParent()) instanceof PsiJavaModule &&
        ((PsiJavaModule)parent).hasModifierProperty(PsiModifier.OPEN)) {
      String message = JavaErrorMessages.message("module.opens.in.weak.module");
      HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(statement).descriptionAndTooltip(message).create();
      QuickFixAction.registerQuickFixAction(info, factory().createModifierListFix((PsiModifierListOwner)parent, PsiModifier.OPEN, false, false));
      QuickFixAction.registerQuickFixAction(info, factory().createDeleteFix(statement));
      return info;
    }

    return null;
  }

  @Nullable
  static HighlightInfo checkPackageReference(@NotNull PsiPackageAccessibilityStatement statement, @NotNull PsiFile file) {
    PsiJavaCodeReferenceElement refElement = statement.getPackageReference();
    if (refElement != null) {
      Module module = findModuleForFile(file);
      if (module != null) {
        PsiElement target = refElement.resolve();
        PsiDirectory[] directories = target instanceof PsiPackage ? ((PsiPackage)target).getDirectories(module.getModuleScope(false)) : null;
        String packageName = statement.getPackageName();
        HighlightInfoType type = statement.getRole() == Role.OPENS ? HighlightInfoType.WARNING : HighlightInfoType.ERROR;
        if (directories == null || directories.length == 0) {
          String message = JavaErrorMessages.message("package.not.found", packageName);
          HighlightInfo info = HighlightInfo.newHighlightInfo(type).range(refElement).descriptionAndTooltip(message).create();
          QuickFixAction.registerQuickFixAction(info, factory().createCreateClassInPackageInModuleFix(module, packageName));
          return info;
        }
        if (packageName != null && PsiUtil.isPackageEmpty(directories, packageName)) {
          String message = JavaErrorMessages.message("package.is.empty", packageName);
          HighlightInfo info = HighlightInfo.newHighlightInfo(type).range(refElement).descriptionAndTooltip(message).create();
          QuickFixAction.registerQuickFixAction(info, factory().createCreateClassInPackageInModuleFix(module, packageName));
          return info;
        }
      }
    }

    return null;
  }

  @NotNull
  static List<HighlightInfo> checkPackageAccessTargets(@NotNull PsiPackageAccessibilityStatement statement) {
    List<HighlightInfo> results = ContainerUtil.newSmartList();

    Set<String> targets = ContainerUtil.newTroveSet();
    for (PsiJavaModuleReferenceElement refElement : statement.getModuleReferences()) {
      String refText = refElement.getReferenceText();
      PsiPolyVariantReference ref = refElement.getReference();
      assert ref != null : statement;
      if (!targets.add(refText)) {
        boolean exports = statement.getRole() == Role.EXPORTS;
        String message = JavaErrorMessages.message(exports ? "module.duplicate.exports.target" : "module.duplicate.opens.target", refText);
        HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(refElement).descriptionAndTooltip(message).create();
        QuickFixAction.registerQuickFixAction(info, factory().createDeleteFix(refElement, QuickFixBundle.message("delete.reference.fix.text")));
        results.add(info);
      }
      else if (ref.multiResolve(true).length == 0) {
        String message = JavaErrorMessages.message("module.not.found", refElement.getReferenceText());
        results.add(HighlightInfo.newHighlightInfo(HighlightInfoType.WARNING).range(refElement).descriptionAndTooltip(message).create());
      }
    }

    return results;
  }

  @Nullable
  static HighlightInfo checkServiceReference(@Nullable PsiJavaCodeReferenceElement refElement) {
    if (refElement != null) {
      PsiElement target = refElement.resolve();
      if (!(target instanceof PsiClass)) {
        String message = JavaErrorMessages.message("cannot.resolve.symbol", refElement.getReferenceName());
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(range(refElement)).descriptionAndTooltip(message).create();
      }
      else if (((PsiClass)target).isEnum()) {
        String message = JavaErrorMessages.message("module.service.enum", ((PsiClass)target).getName());
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(range(refElement)).descriptionAndTooltip(message).create();
      }
    }

    return null;
  }

  @Nullable
  static List<HighlightInfo> checkServiceImplementations(@NotNull PsiProvidesStatement statement, @NotNull PsiFile file) {
    PsiReferenceList implRefList = statement.getImplementationList();
    if (implRefList == null) return null;

    List<HighlightInfo> results = ContainerUtil.newSmartList();
    PsiJavaCodeReferenceElement intRef = statement.getInterfaceReference();
    PsiElement intTarget = intRef != null ? intRef.resolve() : null;

    Set<String> filter = ContainerUtil.newTroveSet();
    for (PsiJavaCodeReferenceElement implRef : implRefList.getReferenceElements()) {
      String refText = implRef.getQualifiedName();
      if (!filter.add(refText)) {
        String message = JavaErrorMessages.message("module.duplicate.impl", refText);
        HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(implRef).descriptionAndTooltip(message).create();
        QuickFixAction.registerQuickFixAction(info, factory().createDeleteFix(implRef, QuickFixBundle.message("delete.reference.fix.text")));
        results.add(info);
        continue;
      }

      if (!(intTarget instanceof PsiClass)) continue;

      PsiElement implTarget = implRef.resolve();
      if (implTarget instanceof PsiClass) {
        PsiClass implClass = (PsiClass)implTarget;

        if (findModuleForFile(file) != findModuleForFile(implClass.getContainingFile())) {
          String message = JavaErrorMessages.message("module.service.alien");
          results.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(range(implRef)).descriptionAndTooltip(message).create());
        }

        PsiMethod provider = ContainerUtil.find(
          implClass.findMethodsByName("provider", false),
          m -> m.hasModifierProperty(PsiModifier.PUBLIC) && m.hasModifierProperty(PsiModifier.STATIC) && m.getParameterList().getParametersCount() == 0);
        if (provider != null) {
          PsiType type = provider.getReturnType();
          PsiClass typeClass = type instanceof PsiClassType ? ((PsiClassType)type).resolve() : null;
          if (!InheritanceUtil.isInheritorOrSelf(typeClass, (PsiClass)intTarget, true)) {
            String message = JavaErrorMessages.message("module.service.provider.type", implClass.getName());
            results.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(range(implRef)).descriptionAndTooltip(message).create());
          }
        }
        else if (InheritanceUtil.isInheritorOrSelf(implClass, (PsiClass)intTarget, true)) {
          if (implClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
            String message = JavaErrorMessages.message("module.service.abstract", implClass.getName());
            results.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(range(implRef)).descriptionAndTooltip(message).create());
          }
          else if (!(ClassUtil.isTopLevelClass(implClass) || implClass.hasModifierProperty(PsiModifier.STATIC))) {
            String message = JavaErrorMessages.message("module.service.inner", implClass.getName());
            results.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(range(implRef)).descriptionAndTooltip(message).create());
          }
          else if (!PsiUtil.hasDefaultConstructor(implClass)) {
            String message = JavaErrorMessages.message("module.service.no.ctor", implClass.getName());
            results.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(range(implRef)).descriptionAndTooltip(message).create());
          }
        }
        else {
          String message = JavaErrorMessages.message("module.service.impl");
          results.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(range(implRef)).descriptionAndTooltip(message).create());
        }
      }
    }

    return results;
  }

  @Nullable
  static HighlightInfo checkClashingReads(@NotNull PsiJavaModule module) {
    Trinity<String, PsiJavaModule, PsiJavaModule> conflict = JavaModuleGraphUtil.findConflict(module);
    if (conflict != null) {
      String message = JavaErrorMessages.message(
        "module.conflicting.reads", module.getName(), conflict.first, conflict.second.getName(), conflict.third.getName());
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(range(module)).descriptionAndTooltip(message).create();
    }

    return null;
  }

  private static HighlightInfo moduleResolveError(PsiJavaModuleReferenceElement refElement, PsiPolyVariantReference ref) {
    if (ref.multiResolve(true).length == 0) {
      String message = JavaErrorMessages.message("module.not.found", refElement.getReferenceText());
      return HighlightInfo.newHighlightInfo(HighlightInfoType.WRONG_REF).range(refElement).descriptionAndTooltip(message).create();
    }
    else if (ref.multiResolve(false).length > 1) {
      String message = JavaErrorMessages.message("module.ambiguous", refElement.getReferenceText());
      return HighlightInfo.newHighlightInfo(HighlightInfoType.WARNING).range(refElement).descriptionAndTooltip(message).create();
    }
    else {
      String message = JavaErrorMessages.message("module.not.on.path", refElement.getReferenceText());
      HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.WRONG_REF).range(refElement).descriptionAndTooltip(message).create();
      factory().registerOrderEntryFixes(new QuickFixActionRegistrarImpl(info), ref);
      return info;
    }
  }

  private static QuickFixFactory factory() {
    return QuickFixFactory.getInstance();
  }

  private static TextRange range(PsiJavaModule module) {
    PsiKeyword kw = PsiTreeUtil.getChildOfType(module, PsiKeyword.class);
    return new TextRange(kw != null ? kw.getTextOffset() : module.getTextOffset(), module.getNameIdentifier().getTextRange().getEndOffset());
  }

  private static PsiElement range(PsiJavaCodeReferenceElement refElement) {
    return ObjectUtils.notNull(refElement.getReferenceNameElement(), refElement);
  }
}
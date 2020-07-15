// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.JavaErrorBundle;
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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.PsiPackageAccessibilityStatement.Role;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import gnu.trove.THashSet;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.intellij.openapi.module.ModuleUtilCore.findModuleForFile;
import static com.intellij.psi.SyntaxTraverser.psiTraverser;

final class ModuleHighlightUtil {
  static HighlightInfo checkPackageStatement(@NotNull PsiPackageStatement statement, @NotNull PsiFile file, @Nullable PsiJavaModule module) {
    if (PsiUtil.isModuleFile(file)) {
      String message = JavaErrorBundle.message("module.no.package");
      HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(statement).descriptionAndTooltip(message).create();
      QuickFixAction.registerQuickFixAction(info, factory().createDeleteFix(statement));
      return info;
    }

    if (module != null) {
      String packageName = statement.getPackageName();
      if (packageName != null) {
        PsiJavaModule origin = JavaModuleGraphUtil.findOrigin(module, packageName);
        if (origin != null) {
          String message = JavaErrorBundle.message("module.conflicting.packages", packageName, origin.getName());
          return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(statement).descriptionAndTooltip(message).create();
        }
      }
    }

    return null;
  }

  static HighlightInfo checkFileName(@NotNull PsiJavaModule element, @NotNull PsiFile file) {
    if (!PsiJavaModule.MODULE_INFO_FILE.equals(file.getName())) {
      String message = JavaErrorBundle.message("module.file.wrong.name");
      HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(range(element)).descriptionAndTooltip(message).create();
      QuickFixAction.registerQuickFixAction(info, factory().createRenameFileFix(PsiJavaModule.MODULE_INFO_FILE));
      return info;
    }

    return null;
  }

  static HighlightInfo checkFileDuplicates(@NotNull PsiJavaModule element, @NotNull PsiFile file) {
    Module module = findModuleForFile(file);
    if (module != null) {
      Project project = file.getProject();
      Collection<VirtualFile> others = FilenameIndex.getVirtualFilesByName(project, PsiJavaModule.MODULE_INFO_FILE, module.getModuleScope());
      if (others.size() > 1) {
        String message = JavaErrorBundle.message("module.file.duplicate");
        HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(range(element)).descriptionAndTooltip(message).create();
        others.stream().map(f -> PsiManager.getInstance(project).findFile(f)).filter(f -> f != file).findFirst().ifPresent(
          duplicate -> QuickFixAction.registerQuickFixAction(info, new GoToSymbolFix(duplicate, JavaErrorBundle
            .message("module.open.duplicate.text")))
        );
        return info;
      }
    }

    return null;
  }

  @NotNull
  static List<HighlightInfo> checkDuplicateStatements(@NotNull PsiJavaModule module) {
    List<HighlightInfo> results = new SmartList<>();
    checkDuplicateRefs(module.getRequires(), st -> st.getModuleName(), "module.duplicate.requires", results);
    checkDuplicateRefs(module.getExports(), st -> st.getPackageName(), "module.duplicate.exports", results);
    checkDuplicateRefs(module.getOpens(), st -> st.getPackageName(), "module.duplicate.opens", results);
    checkDuplicateRefs(module.getUses(), st -> qName(st.getClassReference()), "module.duplicate.uses", results);
    checkDuplicateRefs(module.getProvides(), st -> qName(st.getInterfaceReference()), "module.duplicate.provides", results);
    return results;
  }

  private static <T extends PsiStatement> void checkDuplicateRefs(@NotNull Iterable<? extends T> statements,
                                                                  @NotNull Function<? super T, String> ref,
                                                                  @NotNull @PropertyKey(resourceBundle = JavaErrorBundle.BUNDLE) String key,
                                                                  @NotNull List<? super HighlightInfo> results) {
    Set<String> filter = new THashSet<>();
    for (T statement : statements) {
      String refText = ref.apply(statement);
      if (refText != null && !filter.add(refText)) {
        String message = JavaErrorBundle.message(key, refText);
        HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(statement).descriptionAndTooltip(message).create();
        QuickFixAction.registerQuickFixAction(info, factory().createDeleteFix(statement));
        QuickFixAction.registerQuickFixAction(info, MergeModuleStatementsFix.createFix(statement));
        results.add(info);
      }
    }
  }

  @NotNull
  static List<HighlightInfo> checkUnusedServices(@NotNull PsiJavaModule module, @NotNull PsiFile file) {
    List<HighlightInfo> results = new SmartList<>();

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
              String className = qName(ref);
              String packageName = StringUtil.getPackageName(className);
              if (!exports.contains(packageName) && !uses.contains(className)) {
                String message = JavaErrorBundle.message("module.service.unused");
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

  static HighlightInfo checkFileLocation(@NotNull PsiJavaModule element, @NotNull PsiFile file) {
    VirtualFile vFile = file.getVirtualFile();
    if (vFile != null) {
      VirtualFile root = ProjectFileIndex.SERVICE.getInstance(file.getProject()).getSourceRootForFile(vFile);
      if (root != null && !root.equals(vFile.getParent())) {
        String message = JavaErrorBundle.message("module.file.wrong.location");
        HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(range(element)).descriptionAndTooltip(message).create();
        QuickFixAction.registerQuickFixAction(info, new MoveFileFix(vFile, root, QuickFixBundle.message("move.file.to.source.root.text")));
        return info;
      }
    }

    return null;
  }

  static HighlightInfo checkModuleReference(@NotNull PsiRequiresStatement statement) {
    PsiJavaModuleReferenceElement refElement = statement.getReferenceElement();
    if (refElement != null) {
      PsiJavaModuleReference ref = refElement.getReference();
      assert ref != null : refElement.getParent();
      PsiJavaModule target = ref.resolve();
      if (target == null) {
        if (ref.multiResolve(true).length == 0) {
          String message = JavaErrorBundle.message("module.not.found", refElement.getReferenceText());
          return HighlightInfo.newHighlightInfo(HighlightInfoType.WRONG_REF).range(refElement).descriptionAndTooltip(message).create();
        }
        else if (ref.multiResolve(false).length > 1) {
          String message = JavaErrorBundle.message("module.ambiguous", refElement.getReferenceText());
          return HighlightInfo.newHighlightInfo(HighlightInfoType.WARNING).range(refElement).descriptionAndTooltip(message).create();
        }
        else {
          String message = JavaErrorBundle.message("module.not.on.path", refElement.getReferenceText());
          HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.WRONG_REF).range(refElement).descriptionAndTooltip(message).create();
          factory().registerOrderEntryFixes(new QuickFixActionRegistrarImpl(info), ref);
          return info;
        }
      }
      PsiJavaModule container = (PsiJavaModule)statement.getParent();
      if (target == container) {
        String message = JavaErrorBundle.message("module.cyclic.dependence", container.getName());
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(refElement).descriptionAndTooltip(message).create();
      }
      else {
        Collection<PsiJavaModule> cycle = JavaModuleGraphUtil.findCycle(target);
        if (cycle.contains(container)) {
          Stream<String> stream = cycle.stream().map(PsiJavaModule::getName);
          if (ApplicationManager.getApplication().isUnitTestMode()) stream = stream.sorted();
          String message = JavaErrorBundle.message("module.cyclic.dependence", stream.collect(Collectors.joining(", ")));
          return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(refElement).descriptionAndTooltip(message).create();
        }
      }
    }

    return null;
  }

  static HighlightInfo checkHostModuleStrength(@NotNull PsiPackageAccessibilityStatement statement) {
    PsiElement parent;
    if (statement.getRole() == Role.OPENS &&
        (parent = statement.getParent()) instanceof PsiJavaModule &&
        ((PsiJavaModule)parent).hasModifierProperty(PsiModifier.OPEN)) {
      String message = JavaErrorBundle.message("module.opens.in.weak.module");
      HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(statement).descriptionAndTooltip(message).create();
      QuickFixAction.registerQuickFixAction(info, factory().createModifierListFix((PsiModifierListOwner)parent, PsiModifier.OPEN, false, false));
      QuickFixAction.registerQuickFixAction(info, factory().createDeleteFix(statement));
      return info;
    }

    return null;
  }

  static HighlightInfo checkPackageReference(@NotNull PsiPackageAccessibilityStatement statement, @NotNull PsiFile file) {
    PsiJavaCodeReferenceElement refElement = statement.getPackageReference();
    if (refElement != null) {
      Module module = findModuleForFile(file);
      if (module != null) {
        PsiElement target = refElement.resolve();
        PsiDirectory[] directories = PsiDirectory.EMPTY_ARRAY;
        if (target instanceof PsiPackage) {
          boolean inTests = ModuleRootManager.getInstance(module).getFileIndex().isInTestSourceContent(file.getVirtualFile());
          directories = ((PsiPackage)target).getDirectories(module.getModuleScope(inTests));
        }
        String packageName = statement.getPackageName();
        boolean opens = statement.getRole() == Role.OPENS;
        HighlightInfoType type = opens ? HighlightInfoType.WARNING : HighlightInfoType.ERROR;
        if (directories.length == 0) {
          String message = JavaErrorBundle.message("package.not.found", packageName);
          HighlightInfo info = HighlightInfo.newHighlightInfo(type).range(refElement).descriptionAndTooltip(message).create();
          QuickFixAction.registerQuickFixAction(info, factory().createCreateClassInPackageInModuleFix(module, packageName));
          return info;
        }
        if (packageName != null && isPackageEmpty(directories, packageName, opens)) {
          String message = JavaErrorBundle.message("package.is.empty", packageName);
          HighlightInfo info = HighlightInfo.newHighlightInfo(type).range(refElement).descriptionAndTooltip(message).create();
          QuickFixAction.registerQuickFixAction(info, factory().createCreateClassInPackageInModuleFix(module, packageName));
          return info;
        }
      }
    }

    return null;
  }

  private static boolean isPackageEmpty(PsiDirectory @NotNull [] directories, @NotNull String packageName, boolean anyFile) {
    if (anyFile) {
      return Arrays.stream(directories).noneMatch(dir -> dir.getFiles().length > 0);
    }
    else {
      return PsiUtil.isPackageEmpty(directories, packageName);
    }
  }

  @NotNull
  static List<HighlightInfo> checkPackageAccessTargets(@NotNull PsiPackageAccessibilityStatement statement) {
    List<HighlightInfo> results = new SmartList<>();

    Set<String> targets = new THashSet<>();
    for (PsiJavaModuleReferenceElement refElement : statement.getModuleReferences()) {
      String refText = refElement.getReferenceText();
      PsiJavaModuleReference ref = refElement.getReference();
      assert ref != null : statement;
      if (!targets.add(refText)) {
        boolean exports = statement.getRole() == Role.EXPORTS;
        String message = JavaErrorBundle.message(exports ? "module.duplicate.exports.target" : "module.duplicate.opens.target", refText);
        HighlightInfo info = duplicateReference(refElement, message);
        results.add(info);
      }
      else if (ref.multiResolve(true).length == 0) {
        String message = JavaErrorBundle.message("module.not.found", refElement.getReferenceText());
        results.add(HighlightInfo.newHighlightInfo(HighlightInfoType.WARNING).range(refElement).descriptionAndTooltip(message).create());
      }
    }

    return results;
  }

  static HighlightInfo checkServiceReference(@Nullable PsiJavaCodeReferenceElement refElement) {
    if (refElement != null) {
      PsiElement target = refElement.resolve();
      if (!(target instanceof PsiClass)) {
        String message = JavaErrorBundle.message("cannot.resolve.symbol", refElement.getReferenceName());
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(range(refElement)).descriptionAndTooltip(message).create();
      }
      else if (((PsiClass)target).isEnum()) {
        String message = JavaErrorBundle.message("module.service.enum", ((PsiClass)target).getName());
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(range(refElement)).descriptionAndTooltip(message).create();
      }
    }

    return null;
  }

  @NotNull
  static List<HighlightInfo> checkServiceImplementations(@NotNull PsiProvidesStatement statement, @NotNull PsiFile file) {
    PsiReferenceList implRefList = statement.getImplementationList();
    if (implRefList == null) return Collections.emptyList();

    List<HighlightInfo> results = new SmartList<>();
    PsiJavaCodeReferenceElement intRef = statement.getInterfaceReference();
    PsiElement intTarget = intRef != null ? intRef.resolve() : null;

    Set<String> filter = new THashSet<>();
    for (PsiJavaCodeReferenceElement implRef : implRefList.getReferenceElements()) {
      String refText = implRef.getQualifiedName();
      if (!filter.add(refText)) {
        String message = JavaErrorBundle.message("module.duplicate.impl", refText);
        HighlightInfo info = duplicateReference(implRef, message);
        results.add(info);
        continue;
      }

      if (!(intTarget instanceof PsiClass)) continue;

      PsiElement implTarget = implRef.resolve();
      if (implTarget instanceof PsiClass) {
        PsiClass implClass = (PsiClass)implTarget;

        if (findModuleForFile(file) != findModuleForFile(implClass.getContainingFile())) {
          String message = JavaErrorBundle.message("module.service.alien");
          results.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(range(implRef)).descriptionAndTooltip(message).create());
        }

        PsiMethod provider = ContainerUtil.find(
          implClass.findMethodsByName("provider", false),
          m -> m.hasModifierProperty(PsiModifier.PUBLIC) && m.hasModifierProperty(PsiModifier.STATIC) && m.getParameterList().isEmpty());
        if (provider != null) {
          PsiType type = provider.getReturnType();
          PsiClass typeClass = type instanceof PsiClassType ? ((PsiClassType)type).resolve() : null;
          if (!InheritanceUtil.isInheritorOrSelf(typeClass, (PsiClass)intTarget, true)) {
            String message = JavaErrorBundle.message("module.service.provider.type", implClass.getName());
            results.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(range(implRef)).descriptionAndTooltip(message).create());
          }
        }
        else if (InheritanceUtil.isInheritorOrSelf(implClass, (PsiClass)intTarget, true)) {
          if (implClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
            String message = JavaErrorBundle.message("module.service.abstract", implClass.getName());
            results.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(range(implRef)).descriptionAndTooltip(message).create());
          }
          else if (!(ClassUtil.isTopLevelClass(implClass) || implClass.hasModifierProperty(PsiModifier.STATIC))) {
            String message = JavaErrorBundle.message("module.service.inner", implClass.getName());
            results.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(range(implRef)).descriptionAndTooltip(message).create());
          }
          else if (!PsiUtil.hasDefaultConstructor(implClass)) {
            String message = JavaErrorBundle.message("module.service.no.ctor", implClass.getName());
            results.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(range(implRef)).descriptionAndTooltip(message).create());
          }
        }
        else {
          String message = JavaErrorBundle.message("module.service.impl");
          results.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(range(implRef)).descriptionAndTooltip(message).create());
        }
      }
    }

    return results;
  }

  static HighlightInfo checkClashingReads(@NotNull PsiJavaModule module) {
    Trinity<String, PsiJavaModule, PsiJavaModule> conflict = JavaModuleGraphUtil.findConflict(module);
    if (conflict != null) {
      String message = JavaErrorBundle.message(
        "module.conflicting.reads", module.getName(), conflict.first, conflict.second.getName(), conflict.third.getName());
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(range(module)).descriptionAndTooltip(message).create();
    }

    return null;
  }

  @NotNull
  static List<HighlightInfo> checkModifiers(@NotNull PsiRequiresStatement statement) {
    PsiModifierList modList = statement.getModifierList();
    if (modList != null && PsiJavaModule.JAVA_BASE.equals(statement.getModuleName())) {
      return psiTraverser().children(modList)
          .filter(PsiKeyword.class)
          .map(keyword -> {
            @PsiModifier.ModifierConstant String modifier = keyword.getText();
            String message = JavaErrorBundle.message("modifier.not.allowed", modifier);
            HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(keyword).descriptionAndTooltip(message).create();
            QuickFixAction.registerQuickFixAction(info, factory().createModifierListFix(modList, modifier, false, false));
            return info;
          }).toList();
    }

    return Collections.emptyList();
  }

  @NotNull
  private static QuickFixFactory factory() {
    return QuickFixFactory.getInstance();
  }

  private static @NotNull TextRange range(@NotNull PsiJavaModule module) {
    PsiKeyword kw = PsiTreeUtil.getChildOfType(module, PsiKeyword.class);
    return new TextRange(kw != null ? kw.getTextOffset() : module.getTextOffset(), module.getNameIdentifier().getTextRange().getEndOffset());
  }

  private static @NotNull PsiElement range(@NotNull PsiJavaCodeReferenceElement refElement) {
    return ObjectUtils.notNull(refElement.getReferenceNameElement(), refElement);
  }

  private static HighlightInfo duplicateReference(@NotNull PsiElement refElement, @NotNull String message) {
    HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(refElement).descriptionAndTooltip(message).create();
    QuickFixAction.registerQuickFixAction(info, factory().createDeleteFix(refElement, QuickFixBundle.message("delete.reference.fix.text")));
    return info;
  }

  @Nullable
  public static HighlightInfo checkModulePreviewFeatureAnnotation(@Nullable final PsiStatement statement,
                                                                  @NotNull final LanguageLevel level) {
    if (statement instanceof PsiRequiresStatement) {
      final PsiRequiresStatement requiresStatement = (PsiRequiresStatement)statement;
      final PsiJavaModule module = requiresStatement.resolve();

      return HighlightUtil.checkPreviewFeatureElement(statement, module, level);
    }
    else if (statement instanceof PsiPackageAccessibilityStatement) {
      final PsiPackageAccessibilityStatement accessibilityStatement = (PsiPackageAccessibilityStatement)statement;
      final PsiJavaCodeReferenceElement reference = accessibilityStatement.getPackageReference();
      if (reference == null) return null;

      final PsiElement resolve = reference.resolve();
      if (!(resolve instanceof PsiPackage)) return null;

      final PsiPackage psiPackage = (PsiPackage)resolve;
      return HighlightUtil.checkPreviewFeatureElement(statement, psiPackage, level);
    }
    else if (statement instanceof PsiProvidesStatement) {
      final PsiProvidesStatement providesStatement = (PsiProvidesStatement)statement;
      final PsiReferenceList list = providesStatement.getImplementationList();
      if (list == null) return null;

      return StreamEx.of(list.getReferenceElements())
        .map(PsiReference::resolve)
        .select(PsiClass.class)
        .map(clazz -> HighlightUtil.checkPreviewFeatureElement(statement, clazz, level))
        .nonNull()
        .findAny()
        .orElse(null);
    }
    return null;
  }
}
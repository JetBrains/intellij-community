// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.java19api;

import com.intellij.codeInsight.daemon.impl.analysis.JavaModuleGraphUtil;
import com.intellij.codeInspection.*;
import com.intellij.java.JavaBundle;
import com.intellij.java.codeserver.core.JavaPsiModuleUtil;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.ModuleFileIndex;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.intellij.psi.JavaTokenType.LBRACE;
import static com.intellij.psi.JavaTokenType.RBRACE;
import static com.intellij.psi.PsiJavaModule.MODULE_INFO_FILE;

public class JavaEmptyModuleInfoFileInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final Set<String> JVM_LANGUAGES = Set.of("java", "kt", "kts", "groovy");

  @Override
  public ProblemDescriptor @Nullable [] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
    if (!PsiUtil.isAvailable(JavaFeature.MODULES, file)) return ProblemDescriptor.EMPTY_ARRAY;
    if (!file.getName().equals(MODULE_INFO_FILE)) return ProblemDescriptor.EMPTY_ARRAY;
    if (!(file instanceof PsiJavaFile javaFile)) return ProblemDescriptor.EMPTY_ARRAY;

    PsiJavaModule descriptor = javaFile.getModuleDeclaration();
    if (descriptor == null) return ProblemDescriptor.EMPTY_ARRAY;

    if (!isEmptyModule(descriptor)) return ProblemDescriptor.EMPTY_ARRAY;
    if (!needRequires(descriptor)) return ProblemDescriptor.EMPTY_ARRAY;

    ProblemDescriptor problemDescriptor = manager.createProblemDescriptor(
      file,
      JavaBundle.message("inspection.unresolved.module.dependencies.problem.descriptor"),
      isOnTheFly,
      LocalQuickFix.notNullElements(new GenerateModuleInfoRequiresFix()),
      ProblemHighlightType.WARNING
    );
    return new ProblemDescriptor[]{problemDescriptor};
  }

  private static class GenerateModuleInfoRequiresFix extends PsiUpdateModCommandQuickFix {
    @Override
    public @NotNull String getFamilyName() {
      return JavaBundle.message("inspection.auto.add.module.requirements.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      if (!(element instanceof PsiJavaFile psiJavaFile)) return;
      PsiJavaModule descriptor = psiJavaFile.getModuleDeclaration();
      if (descriptor == null) return;
      if (!isEmptyModule(descriptor)) return;
      Set<PsiJavaModule> modules = walk(descriptor, stmt -> true);

      if (modules.isEmpty()) {
        PsiElement content = getStartContentElement(descriptor);
        PsiElement newLine = PsiParserFacade.getInstance(element.getProject())
          .createWhiteSpaceFromText("\n");
        PsiComment comment = JavaPsiFacade.getElementFactory(element.getProject())
          .createCommentFromText("// no dependencies", null);
        descriptor.addAfter(comment, content);
        descriptor.addAfter(newLine, content);
      }
      else {
        DependencyScope scope = getScope(descriptor);
        for (PsiJavaModule target : modules) {
          JavaModuleGraphUtil.addDependency(descriptor, target, scope);
        }
      }
    }
  }

  private static boolean isEmptyModule(@NotNull PsiJavaModule module) {
    PsiElement element = getStartContentElement(module);
    if (element == null) return false;
    while ((element = element.getNextSibling()) != null) {
      if (element.getNode().getElementType() == RBRACE) return true;
      if (!(element instanceof PsiWhiteSpace)) return false;
    }
    return true;
  }

  private static boolean needRequires(@NotNull PsiJavaModule descriptor) {
    Set<PsiJavaModule> modules = walk(descriptor, psiJavaModule -> psiJavaModule.getName().equals(descriptor.getName()));
    return !modules.isEmpty();
  }

  private static @Nullable PsiElement getStartContentElement(@NotNull PsiJavaModule module) {
    PsiElement child = module.getFirstChild();
    while (child != null && child.getNode().getElementType() != LBRACE) {
      child = child.getNextSibling();
    }
    return child;
  }

  private static Set<PsiJavaModule> walk(@NotNull PsiJavaModule descriptor,
                                         @NotNull Predicate<@NotNull PsiJavaModule> shouldProcessFollowingFile) {
    PsiFile descriptorFile = descriptor.getContainingFile().getOriginalFile();
    Module module = ModuleUtilCore.findModuleForFile(descriptorFile);
    if (module == null) return Set.of();

    PsiManager psiManager = PsiManager.getInstance(module.getProject());
    ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
    ModuleFileIndex fileIndex = rootManager.getFileIndex();

    // collect descriptors
    Map<PsiImportStatement, PsiJavaModule> imports = new HashMap<>();
    ImportsCollector collector = new ImportsCollector(psiManager, statement -> {
      PsiJavaModule result = imports.computeIfAbsent(statement, stmt -> findDescriptor(stmt.resolve()));
      return result == null || shouldProcessFollowingFile.test(result);
    });
    DependencyScope scope = getScope(descriptor);
    for (VirtualFile root : rootManager.getSourceRoots()) {
      DependencyScope currentScope = fileIndex.isInTestSourceContent(root) ? DependencyScope.TEST : DependencyScope.COMPILE;
      if (currentScope == scope) {
        VfsUtilCore.iterateChildrenRecursively(root, file -> file.isDirectory() ||
                                                             (file.getExtension() != null && JVM_LANGUAGES.contains(file.getExtension())),
                                               collector);
      }
    }

    // clean descriptors
    return imports.values().stream()
      .filter(Objects::nonNull)
      .filter(m -> !m.getName().equals(descriptor.getName()))
      .collect(Collectors.toCollection(() -> new TreeSet<>((o1, o2) -> o1.getName().compareToIgnoreCase(o2.getName()))));
  }

  private static @NotNull DependencyScope getScope(@NotNull PsiJavaModule descriptor) {
    PsiFile file = descriptor.getContainingFile().getOriginalFile();
    Module module = ModuleUtilCore.findModuleForFile(file);
    if (module == null) return DependencyScope.COMPILE;

    return ModuleRootManager.getInstance(module).getFileIndex()
             .isInTestSourceContent(file.getVirtualFile())
           ? DependencyScope.TEST
           : DependencyScope.COMPILE;
  }

  private static @Nullable PsiJavaModule findDescriptor(@Nullable PsiElement psiElement) {
    if (psiElement == null) return null;
    if (psiElement instanceof PsiPackage psiPackage) {
      PsiDirectory[] directories = psiPackage.getDirectories(psiPackage.getResolveScope());
      for (PsiDirectory directory : directories) {
        PsiJavaModule descriptor = JavaPsiModuleUtil.findDescriptorByElement(directory);
        if (descriptor != null) return descriptor;
      }
    }
    else {
      return JavaPsiModuleUtil.findDescriptorByElement(psiElement);
    }
    return null;
  }

  private static class ImportsCollector implements ContentIterator {
    private final @NotNull PsiManager myPsiManager;
    private final @NotNull Predicate<PsiImportStatement> myShouldProcessFollowingFile;

    private ImportsCollector(@NotNull PsiManager manager, @NotNull Predicate<PsiImportStatement> shouldProcessFollowingFile) {
      myPsiManager = manager;
      myShouldProcessFollowingFile = shouldProcessFollowingFile;
    }

    @Override
    public boolean processFile(@NotNull VirtualFile fileOrDir) {
      PsiFile file = myPsiManager.findFile(fileOrDir);
      if (file == null) return true;
      if (!(file instanceof PsiJavaFile javaFile)) return true;
      PsiImportList imports = javaFile.getImportList();
      if (imports == null) return true;
      for (PsiImportStatement importStatement : imports.getImportStatements()) {
        if (!myShouldProcessFollowingFile.test(importStatement)) return false;
      }
      return true;
    }
  }
}
// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.jps;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.undo.BasicUndoableAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ExternalProjectSystemRegistry;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.ModuleSourceOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImportStatement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

public final class ClassOverriddenAtRuntimeInspection extends AbstractBaseJavaLocalInspectionTool {

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    VirtualFile file = holder.getFile().getVirtualFile();
    if (file == null) return PsiElementVisitor.EMPTY_VISITOR;

    Module module = ModuleUtilCore.findModuleForFile(file, holder.getProject());
    if (module == null) return PsiElementVisitor.EMPTY_VISITOR;

    // Only for the IntelliJ module model (JPS)
    if (ExternalProjectSystemRegistry.getInstance().getExternalSource(module) != null) return PsiElementVisitor.EMPTY_VISITOR;
    boolean includeTests = ProjectFileIndex.getInstance(holder.getProject()).isInTestSourceContent(file);

    return new JavaElementVisitor() {
      private final Map<String, OverrideInfo> myCache = new HashMap<>();

      @Override
      public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
        checkRef(expression);
      }

      @Override
      public void visitReferenceElement(@NotNull PsiJavaCodeReferenceElement reference) {
        if (reference.getParent() instanceof PsiImportStatement) return;
        checkRef(reference);
      }

      private void checkRef(@NotNull PsiJavaCodeReferenceElement reference) {
        switch (reference.resolve()) {
          case PsiClass cls -> checkExplicitClassReference(reference, cls);
          case PsiMember member -> checkMemberAccess(reference, member);
          case null, default -> {
          }
        }
      }

      private void checkExplicitClassReference(@NotNull PsiJavaCodeReferenceElement reference, @NotNull PsiClass psiClass) {
        if (psiClass.getContainingClass() != null) return;
        String fqn = psiClass.getQualifiedName();
        if (fqn == null) return;
        OverrideInfo info = myCache.computeIfAbsent(fqn, k -> computeOverrideInfo(psiClass, k, module, includeTests));
        if (info == OverrideInfo.EMPTY) return;
        String message = JavaAnalysisBundle.message("class.overridden.at.runtime.problem", fqn, info.runtimeEntryName());
        holder.registerProblem(reference, message, fix(info));
      }

      private void checkMemberAccess(@NotNull PsiJavaCodeReferenceElement reference, @NotNull PsiMember member) {
        if (!(member.getContainingClass() instanceof PsiClass containingClass)) return;
        String fqn = containingClass.getQualifiedName();
        if (fqn == null) return;
        OverrideInfo info = myCache.computeIfAbsent(fqn, k -> computeOverrideInfo(containingClass, k, module, includeTests));
        if (info == OverrideInfo.EMPTY) return;

        // A.test() — the "A" class reference is already flagged above; skip to avoid double warning
        if (reference.getQualifier() instanceof PsiJavaCodeReferenceElement qualifier && qualifier.resolve() instanceof PsiClass) return;

        // test() called via static import — no explicit class in code; the import itself is already flagged
        if (reference instanceof PsiReferenceExpression referenceExpression && referenceExpression.getQualifierExpression() == null) return;

        // Highlight just the member name (e.g. "instanceTest"), not the full qualified expression
        PsiElement element = reference instanceof PsiReferenceExpression re ? re.getReferenceNameElement() : null;
        String message = JavaAnalysisBundle.message("class.overridden.at.runtime.member.problem",
                                                    member.getName(), fqn, info.runtimeEntryName());
        holder.registerProblem(element != null ? element : reference, message, fix(info));
      }

      private static LocalQuickFix @NotNull [] fix(@NotNull OverrideInfo info) {
        return new LocalQuickFix[]{new MoveDepBeforeFix(info.compileEntryName(), info.runtimeEntryName())};
      }
    };
  }

  private static @NotNull OverrideInfo computeOverrideInfo(@NotNull PsiClass compileClass,
                                                           @NotNull String fqn,
                                                           @NotNull Module module,
                                                           boolean includeTests) {
    GlobalSearchScope runtimeScope = module.getModuleRuntimeScope(includeTests);
    PsiClass runtimeClass = JavaPsiFacade.getInstance(module.getProject()).findClass(fqn, runtimeScope);
    if (runtimeClass == null || compileClass.equals(runtimeClass)) return OverrideInfo.EMPTY;

    VirtualFile runtimeFile = PsiUtilCore.getVirtualFile(runtimeClass);
    VirtualFile compileFile = PsiUtilCore.getVirtualFile(compileClass);
    if (runtimeFile == null || compileFile == null) return OverrideInfo.EMPTY;

    ProjectFileIndex fileIndex = ProjectFileIndex.getInstance(module.getProject());
    String runtimeEntryName = findTopLevelEntryName(module, fileIndex.getOrderEntriesForFile(runtimeFile),
                                                    m -> m.getModuleRuntimeScope(includeTests).contains(runtimeFile));
    String compileEntryName = findTopLevelEntryName(module, fileIndex.getOrderEntriesForFile(compileFile),
                                                    m -> containsInExportedCompileScope(m, compileFile, includeTests));
    if (runtimeEntryName == null || compileEntryName == null) return OverrideInfo.EMPTY;

    return new OverrideInfo(runtimeEntryName, compileEntryName);
  }

  private static boolean containsInExportedCompileScope(@NotNull Module module,
                                                        @NotNull VirtualFile file,
                                                        boolean includeTests) {
    OrderEnumerator enumerator = OrderEnumerator.orderEntries(module).recursively().exportedOnly().compileOnly();
    if (!includeTests) enumerator = enumerator.productionOnly();

    if (ContainerUtil.exists(enumerator.classes().usingCache().getRoots(), root -> VfsUtilCore.isAncestor(root, file, false))) return true;
    return ContainerUtil.exists(enumerator.sources().usingCache().getRoots(), root -> VfsUtilCore.isAncestor(root, file, false));
  }

  private record OverrideInfo(@NotNull String runtimeEntryName, @NotNull String compileEntryName) {
    static OverrideInfo EMPTY = new OverrideInfo("", "");
  }

  private static @Nullable String findTopLevelEntryName(@NotNull Module module,
                                                        List<OrderEntry> fileEntries,
                                                        @NotNull Predicate<Module> check) {
    for (OrderEntry direct : ModuleRootManager.getInstance(module).getOrderEntries()) {
      if (direct instanceof ModuleSourceOrderEntry) continue;

      if (direct instanceof ModuleOrderEntry moduleOrderEntry) {
        Module dependencyModule = moduleOrderEntry.getModule();
        if (dependencyModule != null && check.test(dependencyModule)) {
          return entryName(direct);
        }
        continue;
      }

      if (fileEntries.contains(direct)) {
        return entryName(direct);
      }
    }

    return null;
  }

  private static @NonNls @NotNull String entryName(@NotNull OrderEntry entry) {
    if (entry instanceof ModuleOrderEntry moe) return moe.getModuleName();
    if (entry instanceof LibraryOrderEntry loe) return Objects.requireNonNullElse(loe.getLibraryName(), "");
    return entry.getPresentableName();
  }

  static final class MoveDepBeforeFix implements LocalQuickFix {
    private final String myEntryToMove;
    private final String myShadowingEntry;

    MoveDepBeforeFix(@NotNull String entryToMove, @NotNull String shadowingEntry) {
      myEntryToMove = entryToMove;
      myShadowingEntry = shadowingEntry;
    }

    @Override
    public @NotNull String getName() {
      return JavaAnalysisBundle.message("class.overridden.at.runtime.fix.name", myEntryToMove, myShadowingEntry);
    }

    @Override
    public @NotNull String getFamilyName() {
      return JavaAnalysisBundle.message("class.overridden.at.runtime.fix.family");
    }

    @Override
    public boolean startInWriteAction() {
      return false; // we own the WriteCommandAction to guarantee undo registration is inside our command
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getPsiElement();
      if (element == null) return;

      PsiFile containingFile = element.getContainingFile();
      if (containingFile == null) return;

      VirtualFile vFile = containingFile.getVirtualFile();
      if (vFile == null) return;

      Module module = ModuleUtilCore.findModuleForFile(vFile, project);
      if (module == null) return;

      String[] originalOrder = Arrays.stream(ModuleRootManager.getInstance(module).getOrderEntries())
        .map(ClassOverriddenAtRuntimeInspection::entryName)
        .toArray(String[]::new);

      WriteCommandAction.writeCommandAction(project).run(() -> {
        ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
        boolean committed = false;
        try {
          if (!rearrange(model)) return;
          model.commit();
          committed = true;

          UndoManager.getInstance(project).undoableActionPerformed(new BasicUndoableAction(vFile) {
            @Override
            public void undo() {
              if (module.isDisposed()) return;
              ModuleRootModificationUtil.updateModel(module, model -> {
                Map<String, Integer> targetIdx = new HashMap<>();
                for (int i = 0; i < originalOrder.length; i++) {
                  targetIdx.put(originalOrder[i], i);
                }
                OrderEntry[] sorted = model.getOrderEntries().clone();
                Arrays.sort(sorted, Comparator.comparingInt(e -> targetIdx.getOrDefault(entryName(e), Integer.MAX_VALUE)));
                model.rearrangeOrderEntries(sorted);
              });
            }

            @Override
            public void redo() {
              if (module.isDisposed()) return;
              ModuleRootModificationUtil.updateModel(module, model -> rearrange(model));
            }
          });
        }
        finally {
          if (!committed) model.dispose();
        }
      });
    }

    private boolean rearrange(ModifiableRootModel model) {
      OrderEntry[] entries = model.getOrderEntries();
      int moveIdx = index(entries, myEntryToMove);
      int blockerIdx = index(entries, myShadowingEntry);
      if (!move(entries, moveIdx, blockerIdx)) return false;

      model.rearrangeOrderEntries(entries);
      return true;
    }

    private static int index(OrderEntry @NotNull [] entries, @NotNull String expectedName) {
      for (int i = 0; i < entries.length; i++) {
        if (expectedName.equals(entryName(entries[i]))) {
          return i;
        }
      }
      return -1;
    }

    private static boolean move(OrderEntry @NotNull [] entries, int moveIdx, int beforeIdx) {
      if (beforeIdx >= 0 && moveIdx > beforeIdx && moveIdx < entries.length) {
        OrderEntry entryToMove = entries[moveIdx];
        System.arraycopy(entries, beforeIdx, entries, beforeIdx + 1, moveIdx - beforeIdx);
        entries[beforeIdx] = entryToMove;
        return true;
      }
      return false;
    }
  }
}
// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.openapi.command.undo.BasicUndoableAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.psiutils.TestUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public final class SuppressForTestsScopeFix implements LocalQuickFix {

  private final String myShortName;

  private SuppressForTestsScopeFix(InspectionProfileEntry inspection) {
    myShortName = inspection.getShortName();
  }

  public static @Nullable SuppressForTestsScopeFix build(InspectionProfileEntry inspection, PsiElement context) {
    if (!TestUtils.isInTestSourceContent(context)) {
      return null;
    }
    return new SuppressForTestsScopeFix(inspection);
  }

  @Override
  public @NotNull String getFamilyName() {
    return InspectionGadgetsBundle.message("suppress.for.tests.scope.quickfix");
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public void applyFix(final @NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    final PsiElement problemElement = descriptor.getPsiElement();
    if (problemElement == null || !problemElement.isValid()) return;

    addRemoveTestsScope(project, true);
    final VirtualFile vFile = problemElement.getContainingFile().getVirtualFile();
    UndoManager.getInstance(project).undoableActionPerformed(new BasicUndoableAction(vFile) {
      @Override
      public void undo() {
        addRemoveTestsScope(project, false);
      }

      @Override
      public void redo() {
        addRemoveTestsScope(project, true);
      }
    });
  }

  private void addRemoveTestsScope(@NotNull Project project, boolean add) {
    final InspectionProfileImpl profile = InspectionProjectProfileManager.getInstance(project).getCurrentProfile();
    final InspectionToolWrapper<?, ?> tool = profile.getInspectionTool(myShortName, project);
    if (tool == null) {
      return;
    }
    if (add) {
      final NamedScope namedScope = NamedScopesHolder.getScope(project, "Tests");
      assert namedScope != null : "named scope must not be null";
      final HighlightDisplayKey key = HighlightDisplayKey.find(myShortName);
      assert key != null : "highlight display key must not be null";
      final HighlightDisplayLevel level = profile.getErrorLevel(key, namedScope, project);
      profile.addScope(tool, namedScope, level, false, project);
    }
    else {
      profile.removeScope(myShortName, "Tests", project);
    }
    profile.scopesChanged();
  }
}

// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.testIntegration;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.navigation.GotoTargetHandler;
import com.intellij.codeInsight.navigation.NavigationUtil;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.icons.AllIcons;
import com.intellij.lang.LangBundle;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.List;

public class GotoTestOrCodeHandler extends GotoTargetHandler {
  @Override
  protected String getFeatureUsedKey() {
    return "navigation.goto.testOrCode";
  }

  @Override
  @Nullable
  protected GotoData getSourceAndTargetElements(final Editor editor, final PsiFile file) {
    PsiElement selectedElement = getSelectedElement(editor, file);
    PsiElement sourceElement = TestFinderHelper.findSourceElement(selectedElement);
    if (sourceElement == null) return null;

    List<AdditionalAction> actions = new SmartList<>();

    Collection<PsiElement> candidates;
    if (TestFinderHelper.isTest(selectedElement)) {
      candidates = TestFinderHelper.findClassesForTest(selectedElement);
    }
    else {
      candidates = TestFinderHelper.findTestsForClass(selectedElement);
      if (candidates.size() != 1) {
        for (TestCreator creator : LanguageTestCreators.INSTANCE.allForLanguage(file.getLanguage())) {
          if (!creator.isAvailable(file.getProject(), editor, file)) continue;
          actions.add(new AdditionalAction() {
            @NotNull
            @Override
            public String getText() {
              String text = creator instanceof ItemPresentation ? ((ItemPresentation)creator).getPresentableText() : null;
              return ObjectUtils.notNull(text, LangBundle.message("action.create.new.test.text"));
            }

            @Override
            public Icon getIcon() {
              Icon icon = creator instanceof ItemPresentation ? ((ItemPresentation)creator).getIcon(false) : null;
              return ObjectUtils.notNull(icon, AllIcons.Actions.IntentionBulb);
            }

            @Override
            public void execute() {
              creator.createTest(file.getProject(), editor, file);
            }
          });
        }
      }
    }

    return new GotoData(sourceElement, PsiUtilCore.toPsiElementArray(candidates), actions);
  }

  @NotNull
  public static PsiElement getSelectedElement(Editor editor, PsiFile file) {
    return PsiUtilCore.getElementAtOffset(file, editor.getCaretModel().getOffset());
  }

  @Override
  protected boolean shouldSortTargets() {
    return false;
  }

  @NotNull
  @Override
  protected String getChooserTitle(@NotNull PsiElement sourceElement, String name, int length, boolean finished) {
    String suffix = finished ? "" : " so far";
    if (TestFinderHelper.isTest(sourceElement)) {
      return CodeInsightBundle.message("goto.test.chooserTitle.subject", name, length, suffix);
    }
    else {
      return CodeInsightBundle.message("goto.test.chooserTitle.test", name, length, suffix);
    }
  }

  @NotNull
  @Override
  protected String getFindUsagesTitle(@NotNull PsiElement sourceElement, String name, int length) {
    if (TestFinderHelper.isTest(sourceElement)) {
      return CodeInsightBundle.message("goto.test.findUsages.subject.title", name);
    }
    else {
      return CodeInsightBundle.message("goto.test.findUsages.test.title", name);
    }
  }

  @NotNull
  @Override
  protected String getNotFoundMessage(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    return CodeInsightBundle.message("goto.test.notFound");
  }

  @Nullable
  @Override
  protected String getAdText(PsiElement source, int length) {
    if (length > 0 && !TestFinderHelper.isTest(source)) {
      final Shortcut shortcut = KeymapUtil.getPrimaryShortcut(DefaultRunExecutor.getRunExecutorInstance().getContextActionId());
      if (shortcut != null) {
        return (LangBundle.message("popup.advertisement.press.to.run.selected.tests", KeymapUtil.getShortcutText(shortcut)));
      }
    }
    return null;
  }

  @Override
  protected boolean useEditorFont() {
    return false;
  }

  @Override
  protected void navigateToElement(@NotNull Navigatable element) {
    if (element instanceof PsiElement) {
      NavigationUtil.activateFileWithPsiElement((PsiElement)element, true);
    }
    else {
      element.navigate(true);
    }
  }
}

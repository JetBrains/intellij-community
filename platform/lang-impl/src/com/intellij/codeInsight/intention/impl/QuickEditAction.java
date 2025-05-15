/*
 * Copyright 2006 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.lang.Language;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Objects;

/**
 * "Quick Edit Language" intention action that provides an editor which shows an injected language
 * fragment's complete prefix and suffix in non-editable areas and allows to edit the fragment
 * without having to consider any additional escaping rules (e.g. when editing regexes in String
 * literals).
 *
 * @author Gregory Shrago
 * @author Konstantin Bulenkov
 */
public class QuickEditAction extends QuickEditActionKeys implements IntentionAction, LowPriorityAction {
  public static final Key<QuickEditHandler> QUICK_EDIT_HANDLER = Key.create("QUICK_EDIT_HANDLER");
  private String myLastLanguageName;

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
    return getRangePair(psiFile, editor) != null;
  }

  protected @Nullable Pair<PsiElement, TextRange> getRangePair(PsiFile file, Editor editor) {
    int offset = editor.getCaretModel().getOffset();
    PsiLanguageInjectionHost host = PsiTreeUtil.getParentOfType(file.findElementAt(offset), PsiLanguageInjectionHost.class, false);
    if (host == null || host instanceof PsiComment || ElementManipulators.getManipulator(host) == null) return null;
    List<Pair<PsiElement, TextRange>> injections = InjectedLanguageManager.getInstance(host.getProject()).getInjectedPsiFiles(host);
    if (injections == null || injections.isEmpty()) return null;
    int offsetInElement = offset - host.getTextRange().getStartOffset();
    Pair<PsiElement, TextRange> rangePair =
      ContainerUtil.find(injections, pair -> pair.second.containsRange(offsetInElement, offsetInElement));
    if (rangePair != null) {
      Language language = rangePair.first.getContainingFile().getLanguage();


      Object action = host.getUserData(EDIT_ACTION_AVAILABLE);
      if (action == null) {
        action = language.getUserData(EDIT_ACTION_AVAILABLE);
      }
      if (action != null && action.equals(false)) return null;

      myLastLanguageName = language.getDisplayName();
    }
    return rangePair;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
    invokeImpl(project, editor, psiFile);
  }

  public QuickEditHandler invokeImpl(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    int offset = editor.getCaretModel().getOffset();
    Pair<PsiElement, TextRange> pair = Objects.requireNonNull(getRangePair(file, editor));

    PsiFile injectedFile = (PsiFile)pair.first;
    QuickEditHandler handler = getHandler(project, injectedFile, editor, file);

    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      DocumentWindow documentWindow = InjectedLanguageUtil.getDocumentWindow(injectedFile);
      if (documentWindow != null) {
        handler.navigate(InjectedLanguageUtil.hostToInjectedUnescaped(documentWindow, offset));
      }
    }
    return handler;
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  private @NotNull QuickEditHandler getHandler(Project project, PsiFile injectedFile, Editor editor, PsiFile origFile) {
    QuickEditHandler handler = getExistingHandler(injectedFile);
    if (handler != null && handler.isValid()) {
      return handler;
    }
    handler = new QuickEditHandler(project, injectedFile, origFile, editor, this);
    Disposer.register(project, handler);
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      // todo remove and hide QUICK_EDIT_HANDLER
      injectedFile.putUserData(QUICK_EDIT_HANDLER, handler);
    }
    return handler;
  }

  public static QuickEditHandler getExistingHandler(@NotNull PsiFile injectedFile) {
    DocumentWindow documentWindow = InjectedLanguageUtil.getDocumentWindow(injectedFile);
    if (documentWindow == null) return null;

    Segment[] hostRanges = documentWindow.getHostRanges();
    TextRange hostRange = TextRange.create(hostRanges[0].getStartOffset(),
                                           hostRanges[hostRanges.length - 1].getEndOffset());
    for (Editor editor : EditorFactory.getInstance().getAllEditors()) {
      QuickEditHandler handler = editor.getUserData(QUICK_EDIT_HANDLER);
      if (handler != null && handler.tryReuse(injectedFile, hostRange)) return handler;
    }
    return null;
  }

  protected boolean isShowInBalloon() {
    return false;
  }

  protected @Nullable JComponent createBalloonComponent(@NotNull PsiFile file) {
    return null;
  }

  @Override
  public @NotNull String getText() {
    return CodeInsightBundle.message(
      "intention.text.edit.0.fragment",
      StringUtil.notNullize(myLastLanguageName, CodeInsightBundle.message("name.for.injected.file.default.lang.name"))
    );
  }

  @Override
  public @NotNull String getFamilyName() {
    return CodeInsightBundle.message("intention.family.edit.injected.fragment");
  }

  public static Balloon.Position getBalloonPosition(Editor editor) {
    int line = editor.getCaretModel().getVisualPosition().line;
    Rectangle area = editor.getScrollingModel().getVisibleArea();
    int startLine = area.y / editor.getLineHeight() + 1;
    return (line - startLine) * editor.getLineHeight() < 200 ? Balloon.Position.below : Balloon.Position.above;
  }
}

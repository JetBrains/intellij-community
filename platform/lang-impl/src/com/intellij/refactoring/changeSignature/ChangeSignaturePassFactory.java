/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.refactoring.changeSignature;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactory;
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.UpdateHighlightersUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

public class ChangeSignaturePassFactory extends AbstractProjectComponent implements TextEditorHighlightingPassFactory {
  public ChangeSignaturePassFactory(Project project, TextEditorHighlightingPassRegistrar highlightingPassRegistrar) {
    super(project);
    highlightingPassRegistrar.registerTextEditorHighlightingPass(this, new int[]{Pass.UPDATE_ALL}, null, false, -1);
  }

  @Override
  public TextEditorHighlightingPass createHighlightingPass(@NotNull final PsiFile file, @NotNull final Editor editor) {
    LanguageChangeSignatureDetector detector =
      LanguageChangeSignatureDetectors.INSTANCE.forLanguage(file.getLanguage());
    if (detector == null) return null;

    return new ChangeSignaturePass(file.getProject(), file, editor);
  }

  private static class ChangeSignaturePass extends TextEditorHighlightingPass {
    @NonNls private static final String SIGNATURE_SHOULD_BE_POSSIBLY_CHANGED = "Signature change was detected";
    private final Project myProject;
    private final PsiFile myFile;
    private final Editor myEditor;

    private TextRange myRange;

    public ChangeSignaturePass(Project project, PsiFile file, Editor editor) {
      super(project, editor.getDocument(), false);
      myProject = project;
      myFile = file;
      myEditor = editor;
    }

    @Override
    public void doCollectInformation(@NotNull ProgressIndicator progress) {
      myRange = null;
      final ChangeSignatureGestureDetector detector = ChangeSignatureGestureDetector.getInstance(myProject);
      final ChangeInfo changeInfo = detector.getInitialChangeInfo(myFile);
      if (changeInfo != null) {
        final PsiElement element = changeInfo.getMethod();
        int offset = myEditor.getCaretModel().getOffset();
        if (element == null || !element.isValid()) return;
        final TextRange elementTextRange = element.getTextRange();
        if (elementTextRange == null || !elementTextRange.contains(offset)) return;
        final TextRange range = getHighlightingRange(changeInfo);
        if (range != null && detector.isChangeSignatureAvailable(element)) {
          myRange = range;
        }
      }
    }

    @Override
    public void doApplyInformationToEditor() {
      HighlightInfo info = null;
      if (myRange != null)  {
        TextAttributes attributes = new TextAttributes(null, null,
                                                       myEditor.getColorsScheme().getAttributes(CodeInsightColors.WEAK_WARNING_ATTRIBUTES)
                                                         .getEffectColor(),
                                                       null, Font.PLAIN);
        HighlightInfo.Builder builder = HighlightInfo.newHighlightInfo(HighlightInfoType.INFORMATION).range(myRange);
        builder.textAttributes(attributes);
        builder.descriptionAndTooltip(SIGNATURE_SHOULD_BE_POSSIBLY_CHANGED);
        info = builder.createUnconditionally();
        final ArrayList<IntentionAction> options = new ArrayList<>();
        options.add(new DismissNewSignatureIntentionAction());
        QuickFixAction.registerQuickFixAction(info, new ChangeSignatureDetectorAction(), options, null);
      }
      Collection<HighlightInfo> infos = info != null ? Collections.singletonList(info) : Collections.<HighlightInfo>emptyList();
      UpdateHighlightersUtil.setHighlightersToEditor(myProject, myDocument, 0, myFile.getTextLength(), infos, getColorsScheme(), getId());
    }

    @Nullable
    private static TextRange getHighlightingRange(ChangeInfo changeInfo) {
      if (changeInfo == null) return null;
      final LanguageChangeSignatureDetector detector = LanguageChangeSignatureDetectors.INSTANCE.forLanguage(changeInfo.getLanguage());
      return detector != null ? detector.getHighlightingRange(changeInfo) : null;
    }
  }
}

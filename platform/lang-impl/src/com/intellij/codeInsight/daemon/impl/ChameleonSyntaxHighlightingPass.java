/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactory;
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.ILazyParseableElementType;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.TreeTraversal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.openapi.util.Conditions.*;
import static com.intellij.psi.SyntaxTraverser.psiApi;
import static com.intellij.psi.SyntaxTraverser.psiTraverser;

class ChameleonSyntaxHighlightingPass extends TextEditorHighlightingPass {

  public static class Factory extends AbstractProjectComponent implements TextEditorHighlightingPassFactory {

    protected Factory(Project project, TextEditorHighlightingPassRegistrar registrar) {
      super(project);
      registrar.registerTextEditorHighlightingPass(this, TextEditorHighlightingPassRegistrar.Anchor.FIRST, -1, false, false);
    }

    @Nullable
    @Override
    public TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile file, @NotNull Editor editor) {
      return new ChameleonSyntaxHighlightingPass(myProject, file, editor);
    }
  }

  private final Editor myEditor;
  private final PsiFile myFile;

  private final int myStartOffset;
  private final int myEndOffset;

  public ChameleonSyntaxHighlightingPass(@NotNull Project project, @NotNull final PsiFile file, @NotNull Editor editor) {
    super(project, editor.getDocument(), false);
    ApplicationManager.getApplication().assertIsDispatchThread();

    myEditor = editor;

    TextRange range = VisibleHighlightingPassFactory.calculateVisibleRange(myEditor);
    myStartOffset = range.getStartOffset();
    myEndOffset = range.getEndOffset();

    myFile = file;
  }

  @Override
  public void doCollectInformation(@NotNull ProgressIndicator progress) {
  }

  @Override
  public void doApplyInformationToEditor() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    EditorColorsScheme scheme = myEditor.getColorsScheme();
    TextAttributes defaultAttrs = scheme.getAttributes(HighlighterColors.TEXT);

    SyntaxTraverser<PsiElement> s = psiTraverser(myFile).
      expand(compose(psiApi().TO_RANGE(), new Condition<TextRange>() {
        @Override
        public boolean value(TextRange range) {
          return range.intersects(myStartOffset, myEndOffset);
        }
      })).filterTypes(instanceOf(ILazyParseableElementType.class)).filterTypes(notInstanceOf(IFileElementType.class));
    List<HighlightInfo> infos = ContainerUtil.newArrayList();


    for (PsiElement e : s) {
      Language language = ILazyParseableElementType.LANGUAGE_KEY.get(e.getNode());
      if (language == null) continue;

      SyntaxHighlighter syntaxHighlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(language, myProject, myFile.getVirtualFile());
      for (PsiElement token : psiTraverser(e).traverse(TreeTraversal.LEAVES_DFS)) {
        TextRange tr = token.getTextRange();
        if (tr.isEmpty()) continue;
        IElementType type = PsiUtilCore.getElementType(token);
        TextAttributesKey[] keys = syntaxHighlighter.getTokenHighlights(type);

        // force attribute colors to override host' ones
        TextAttributes attributes = null;
        for (TextAttributesKey key : keys) {
          TextAttributes attrs2 = scheme.getAttributes(key);
          if (attrs2 != null) {
            attributes = attributes == null ? attrs2 : TextAttributes.merge(attributes, attrs2);
          }
        }
        TextAttributes forcedAttributes;
        if (attributes == null || attributes.isEmpty() || attributes.equals(defaultAttrs)) {
          forcedAttributes = TextAttributes.ERASE_MARKER;
        }
        else {
          infos.add(HighlightInfo.newHighlightInfo(HighlightInfoType.INJECTED_LANGUAGE_FRAGMENT).
            range(tr).
            textAttributes(TextAttributes.ERASE_MARKER).
            createUnconditionally());

          forcedAttributes = new TextAttributes(attributes.getForegroundColor(), attributes.getBackgroundColor(),
                                                attributes.getEffectColor(), attributes.getEffectType(), attributes.getFontType());
        }

        infos.add(HighlightInfo.newHighlightInfo(HighlightInfoType.INJECTED_LANGUAGE_FRAGMENT).
          range(tr).
          textAttributes(forcedAttributes).
          createUnconditionally());
      }
    }
    UpdateHighlightersUtil.setHighlightersToEditor(myProject, myDocument, myStartOffset, myEndOffset, infos, getColorsScheme(), getId());
  }


}

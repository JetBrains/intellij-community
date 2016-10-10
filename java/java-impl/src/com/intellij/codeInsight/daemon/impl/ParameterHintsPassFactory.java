/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.codeHighlighting.EditorBoundHighlightingPass;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactory;
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.util.containers.HashSet;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class ParameterHintsPassFactory extends AbstractProjectComponent implements TextEditorHighlightingPassFactory {
  private static final Key<Boolean> REPEATED_PASS = Key.create("RepeatedParameterHintsPass");

  public ParameterHintsPassFactory(Project project, TextEditorHighlightingPassRegistrar registrar) {
    super(project);
    registrar.registerTextEditorHighlightingPass(this, null, null, false, -1);
  }

  @Nullable
  @Override
  public TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile file, @NotNull Editor editor) {
    return new ParameterHintsPass(file, editor);
  }

  private static class ParameterHintsPass extends EditorBoundHighlightingPass {
    private final Map<Integer, String> myAnnotations = new HashMap<>();

    private ParameterHintsPass(@NotNull PsiFile file, @NotNull Editor editor) {
      super(editor, file, true);
    }

    @Override
    public void doCollectInformation(@NotNull ProgressIndicator progress) {
      assert myDocument != null;
      myAnnotations.clear();
      if (!isEnabled() || !(myFile instanceof PsiJavaFile)) return;
      PsiJavaFile file = (PsiJavaFile) myFile;
      
      SyntaxTraverser.psiTraverser(file).forEach(element -> process(element));
    }

    private static boolean isEnabled() {
      return EditorSettingsExternalizable.getInstance().isShowParameterNameHints();
    }

    private void process(PsiElement child) {
      if (child instanceof PsiCallExpression) {
        inlineLiteralArgumentsNames((PsiCallExpression)child);
      }
    }
    
    private void inlineLiteralArgumentsNames(@NotNull PsiCallExpression expression) {
      ParameterNameHintsManager manager = new ParameterNameHintsManager(expression);
      for (InlayInfo info : manager.getDescriptors()) {
        myAnnotations.put(info.getOffset(), info.getText());
      }
    }

    @Override
    public void doApplyInformationToEditor() {
      assert myDocument != null;
      boolean firstTime = myEditor.getUserData(REPEATED_PASS) == null;
      ParameterHintsPresentationManager presentationManager = ParameterHintsPresentationManager.getInstance();
      Set<String> removedHints = new HashSet<>();
      TIntObjectHashMap<Caret> caretMap = new TIntObjectHashMap<>();
      for (Caret caret : myEditor.getCaretModel().getAllCarets()) {
        caretMap.put(caret.getOffset(), caret);
      }
      for (Inlay inlay : myEditor.getInlayModel().getInlineElementsInRange(0, myDocument.getTextLength())) {
        if (!presentationManager.isParameterHint(inlay)) continue;
        int offset = inlay.getOffset();
        String newText = myAnnotations.remove(offset);
        if (delayRemoval(inlay, caretMap)) continue;
        String oldText = presentationManager.getHintText(inlay);
        if (!Objects.equals(newText, oldText)) {
          if (newText == null) {
            removedHints.add(oldText);
            presentationManager.deleteHint(myEditor, inlay);
          }
          else {
            presentationManager.replaceHint(myEditor, inlay, newText);
          }
        }
      }
      for (Map.Entry<Integer, String> e : myAnnotations.entrySet()) {
        int offset = e.getKey();
        String text = e.getValue();
        presentationManager.addHint(myEditor, offset, text, !firstTime && !removedHints.contains(text));
      }
      myEditor.putUserData(REPEATED_PASS, Boolean.TRUE);
    }

    private boolean delayRemoval(Inlay inlay, TIntObjectHashMap<Caret> caretMap) {
      int offset = inlay.getOffset();
      Caret caret = caretMap.get(offset);
      if (caret == null) return false;
      char afterCaret = myEditor.getDocument().getImmutableCharSequence().charAt(offset);
      if (afterCaret != ',' && afterCaret != ')') return false;
      VisualPosition afterInlayPosition = myEditor.offsetToVisualPosition(offset, true, false);
      // check whether caret is to the right of inlay
      if (!caret.getVisualPosition().equals(afterInlayPosition)) return false;
      return true;
    }
  }
}

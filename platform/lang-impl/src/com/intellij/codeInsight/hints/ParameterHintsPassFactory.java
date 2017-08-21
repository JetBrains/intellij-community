/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInsight.hints;

import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactory;
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar;
import com.intellij.lang.Language;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ParameterHintsPassFactory extends AbstractProjectComponent implements TextEditorHighlightingPassFactory {
  protected static final Key<Long> PSI_MODIFICATION_STAMP = Key.create("psi.modification.stamp");

  public ParameterHintsPassFactory(Project project, TextEditorHighlightingPassRegistrar registrar) {
    super(project);
    registrar.registerTextEditorHighlightingPass(this, null, null, false, -1);
  }

  @Nullable
  @Override
  public TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile file, @NotNull Editor editor) {
    if (editor.isOneLineMode()) return null;
    long currentStamp = getCurrentModificationStamp(file);
    Long savedStamp = editor.getUserData(PSI_MODIFICATION_STAMP);
    if (savedStamp != null && savedStamp == currentStamp) return null;
    Language language = file.getLanguage();
    InlayParameterHintsProvider provider = InlayParameterHintsExtension.INSTANCE.forLanguage(language);
    if (provider == null) return null;
    return new ParameterHintsPass(file, editor, MethodInfoBlacklistFilter.forLanguage(language), false);
  }

  public static long getCurrentModificationStamp(@NotNull PsiFile file) {
    return file.getManager().getModificationTracker().getModificationCount();
  }

  public static void forceHintsUpdateOnNextPass() {
    for (Editor editor : EditorFactory.getInstance().getAllEditors()) {
      forceHintsUpdateOnNextPass(editor);
    }
  }

  public static void forceHintsUpdateOnNextPass(@NotNull Editor editor) {
    editor.putUserData(PSI_MODIFICATION_STAMP, null);
  }

  protected static void putCurrentPsiModificationStamp(@NotNull Editor editor, @NotNull PsiFile file) {
    editor.putUserData(PSI_MODIFICATION_STAMP, getCurrentModificationStamp(file));
  }

}
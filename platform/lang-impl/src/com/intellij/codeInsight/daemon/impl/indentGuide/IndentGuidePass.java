// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.indentGuide;

import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.IndentGuideDescriptor;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.markup.CustomHighlighterRenderer;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.*;


public final class IndentGuidePass extends TextEditorHighlightingPass implements DumbAware {
  private static final Key<Long> LAST_TIME_INDENTS_BUILT = Key.create("LAST_TIME_INDENTS_BUILT");
  private static final CustomHighlighterRenderer INDENT_RENDERER = new IndentGuideRenderer();

  private final Editor myEditor;
  private final PsiFile myPsiFile;
  private final IndentGuides myIndentGuides;

  public IndentGuidePass(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    super(project, editor.getDocument(), false);
    myEditor = editor;
    myPsiFile = psiFile;
    myIndentGuides = new IndentGuides(editor.getDocument(), INDENT_RENDERER);
  }

  @Override
  public void doCollectInformation(@NotNull ProgressIndicator progress) {
    Long stamp = myEditor.getUserData(LAST_TIME_INDENTS_BUILT);
    if (stamp != null && stamp.longValue() == nowStamp()) {
      return;
    }
    myIndentGuides.buildIndents(myEditor, myPsiFile);
  }

  @Override
  public void doApplyInformationToEditor() {
    Long stamp = myEditor.getUserData(LAST_TIME_INDENTS_BUILT);
    if (stamp != null && stamp.longValue() == nowStamp()) {
      return;
    }
    myIndentGuides.applyIndents(myEditor);
    myEditor.putUserData(LAST_TIME_INDENTS_BUILT, nowStamp());
  }

  private long nowStamp() {
    if (!myEditor.getSettings().isIndentGuidesShown()) return -1;
    // include tab size in stamp to make sure indent guides are recalculated on tab size change
    return myDocument.getModificationStamp() ^ (((long)getTabSize()) << 24);
  }

  private int getTabSize() {
    return EditorUtil.getTabSize(myEditor);
  }

  @TestOnly
  public @NotNull List<IndentGuideDescriptor> getDescriptors() {
    return myIndentGuides.getDescriptors();
  }
}

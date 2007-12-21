package com.intellij.codeInsight.folding;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.fileEditor.impl.text.CodeFoldingState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;

public abstract class CodeFoldingManager {
  public static CodeFoldingManager getInstance(Project project){
    return project.getComponent(CodeFoldingManager.class);
  }

  public abstract void updateFoldRegions(Editor editor);

  @Nullable
  public abstract Runnable updateFoldRegionsAsync(Editor editor);

  public abstract FoldRegion findFoldRegion(Editor editor, int startOffset, int endOffset);
  public abstract FoldRegion[] getFoldRegionsAtOffset(Editor editor, int offset);

  public abstract CodeFoldingState saveFoldingState(Editor editor);
  public abstract void restoreFoldingState(Editor editor, CodeFoldingState state);
  
  public abstract void writeFoldingState(CodeFoldingState state, Element element) throws WriteExternalException;
  public abstract CodeFoldingState readFoldingState(Element element, Document document);
}

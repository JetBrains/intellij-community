package com.intellij.codeInsight.folding;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.fileEditor.impl.text.CodeFoldingState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class CodeFoldingManager {
  public static CodeFoldingManager getInstance(Project project){
    return project.getComponent(CodeFoldingManager.class);
  }

  public abstract void updateFoldRegions(@NotNull Editor editor);

  public abstract void forceDefaultState(@NotNull Editor editor);

  @Nullable
  public abstract Runnable updateFoldRegionsAsync(@NotNull Editor editor, boolean firstTime);

  public abstract FoldRegion findFoldRegion(@NotNull Editor editor, int startOffset, int endOffset);
  public abstract FoldRegion[] getFoldRegionsAtOffset(@NotNull Editor editor, int offset);

  public abstract CodeFoldingState saveFoldingState(@NotNull Editor editor);
  public abstract void restoreFoldingState(@NotNull Editor editor, @NotNull CodeFoldingState state);
  
  public abstract void writeFoldingState(@NotNull CodeFoldingState state, @NotNull Element element) throws WriteExternalException;
  public abstract CodeFoldingState readFoldingState(@NotNull Element element, @NotNull Document document);

  public abstract void releaseFoldings(Editor editor);
  public abstract void buildInitialFoldings(Editor editor);
}

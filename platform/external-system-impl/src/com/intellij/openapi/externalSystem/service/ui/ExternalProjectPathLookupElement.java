// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.ui;

import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.FoldingModel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class ExternalProjectPathLookupElement extends LookupElement {
  
  private final @NotNull String myProjectName;
  private final @NotNull String myProjectPath;

  public ExternalProjectPathLookupElement(@NotNull String projectName, @NotNull String projectPath) {
    myProjectName = projectName;
    myProjectPath = projectPath;
  }

  @Override
  public @NotNull String getLookupString() {
    return myProjectName;
  }

  @Override
  public void handleInsert(@NotNull InsertionContext context) {
    Editor editor = context.getEditor();
    final FoldingModel foldingModel = editor.getFoldingModel();
    foldingModel.runBatchFoldingOperation(() -> {
      FoldRegion[] regions = foldingModel.getAllFoldRegions();
      for (FoldRegion region : regions) {
        foldingModel.removeFoldRegion(region);
      }
    });
    
    final Document document = editor.getDocument();
    final int startOffset = context.getStartOffset();
    
    document.replaceString(startOffset, document.getTextLength(), myProjectPath);
    final Project project = context.getProject();
    PsiDocumentManager.getInstance(project).commitDocument(document);
    
    foldingModel.runBatchFoldingOperationDoNotCollapseCaret(() -> {
      FoldRegion region = foldingModel.addFoldRegion(startOffset, startOffset + myProjectPath.length(), myProjectName);
      if (region != null) {
        region.setExpanded(false);
      }
    });
  }
}

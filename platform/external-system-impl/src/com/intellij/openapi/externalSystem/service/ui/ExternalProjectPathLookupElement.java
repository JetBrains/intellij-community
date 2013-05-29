/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.externalSystem.service.ui;

import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.FoldingModel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import org.jetbrains.annotations.NotNull;

/**
 * @author Denis Zhdanov
 * @since 5/25/13 7:26 PM
 */
public class ExternalProjectPathLookupElement extends LookupElement {
  
  @NotNull private final String myProjectName;
  @NotNull private final String myProjectPath;

  public ExternalProjectPathLookupElement(@NotNull String projectName, @NotNull String projectPath) {
    myProjectName = projectName;
    myProjectPath = projectPath;
  }

  @NotNull
  @Override
  public String getLookupString() {
    return myProjectName;
  }

  @Override
  public void handleInsert(InsertionContext context) {
    Editor editor = context.getEditor();
    final FoldingModel foldingModel = editor.getFoldingModel();
    foldingModel.runBatchFoldingOperation(new Runnable() {
      @Override
      public void run() {
        FoldRegion[] regions = foldingModel.getAllFoldRegions();
        for (FoldRegion region : regions) {
          foldingModel.removeFoldRegion(region);
        }
      }
    });
    
    final Document document = editor.getDocument();
    final int startOffset = context.getStartOffset();
    
    document.replaceString(startOffset, document.getTextLength(), myProjectPath);
    final Project project = context.getProject();
    PsiDocumentManager.getInstance(project).commitDocument(document);
    
    foldingModel.runBatchFoldingOperationDoNotCollapseCaret(new Runnable() {
      @Override
      public void run() {
        FoldRegion region = foldingModel.addFoldRegion(startOffset, startOffset + myProjectPath.length(), myProjectName);
        if (region != null) {
          region.setExpanded(false);
        }
      }
    });
  }
}

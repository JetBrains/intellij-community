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
package com.intellij.execution.filters;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public final class OpenFileHyperlinkInfo implements FileHyperlinkInfo {

  private static final int UNDEFINED_OFFSET = -1;

  private final Project myProject;
  private final VirtualFile myFile;
  private final int myDocumentLine;
  private final int myDocumentColumn;

  public OpenFileHyperlinkInfo(@NotNull OpenFileDescriptor descriptor) {
    this(descriptor.getProject(), descriptor.getFile(), descriptor.getLine(), descriptor.getColumn());
  }

  public OpenFileHyperlinkInfo(@NotNull Project project, @NotNull VirtualFile file,
                               int documentLine, int documentColumn) {
    myProject = project;
    myFile = file;
    myDocumentLine = documentLine;
    myDocumentColumn = documentColumn;
  }

  public OpenFileHyperlinkInfo(@NotNull Project project, @NotNull final VirtualFile file, final int line) {
    this(project, file, line, 0);
  }

  @Override
  public OpenFileDescriptor getDescriptor() {
    if (!myFile.isValid()) {
      return null;
    }

    int line = myDocumentLine;
    FileDocumentManager.getInstance().getDocument(myFile); // need to load decompiler text
    LineNumbersMapping mapping = myFile.getUserData(LineNumbersMapping.LINE_NUMBERS_MAPPING_KEY);
    if (mapping != null) {
      line = mapping.bytecodeToSource(myDocumentLine + 1) - 1;
      if (line < 0) {
        line = myDocumentLine;
      }
    }

    int offset = calculateOffset(myFile, line, myDocumentColumn);
    if (offset != UNDEFINED_OFFSET) {
      return new OpenFileDescriptor(myProject, myFile, offset);
    }
    // although document position != logical position, it seems better than returning 'null'
    return new OpenFileDescriptor(myProject, myFile, line, myDocumentColumn);
  }

  @Override
  public void navigate(final Project project) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        OpenFileDescriptor descriptor = getDescriptor();
        if (descriptor != null) {
          FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
        }
      }
    });
  }

  /**
   * Calculates an offset, that matches given line and column of the document.
   *
   * @param file           VirtualFile instance
   * @param documentLine   zero-based line of the document
   * @param documentColumn zero-based column of the document
   * @return calculated offset or UNDEFINED_OFFSET if it's impossible to calculate
   */
  private static int calculateOffset(@NotNull final VirtualFile file,
                                     final int documentLine, final int documentColumn) {
    return ApplicationManager.getApplication().runReadAction(new Computable<Integer>() {

      @Override
      public Integer compute() {
        Document document = FileDocumentManager.getInstance().getDocument(file);
        if (document != null) {
          int lineCount = document.getLineCount();
          if (0 <= documentLine && documentLine < lineCount) {
            int lineStartOffset = document.getLineStartOffset(documentLine);
            int lineEndOffset = document.getLineEndOffset(documentLine);
            int fixedColumn = Math.min(Math.max(documentColumn, 0), lineEndOffset - lineStartOffset);
            return lineStartOffset + fixedColumn;
          }
        }
        return UNDEFINED_OFFSET;
      }
    });
  }
}

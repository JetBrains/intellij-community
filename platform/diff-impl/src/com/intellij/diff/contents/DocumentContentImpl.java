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
package com.intellij.diff.contents;

import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.LineCol;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.util.LineSeparator;
import com.intellij.util.diff.Diff;
import com.intellij.util.diff.FilesTooBigForDiffException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;

/**
 * Allows to compare some text associated with document.
 */
public class DocumentContentImpl extends DiffContentBase implements DocumentContent {
  @Nullable private final Project myProject;

  @NotNull private final Document myDocument;

  @Nullable private final FileType myType;
  @Nullable private final VirtualFile myHighlightFile;

  @Nullable private final LineSeparator mySeparator;
  @Nullable private final Charset myCharset;
  @Nullable private final Boolean myBOM;

  public DocumentContentImpl(@NotNull Document document) {
    this(null, document, null, null, null, null, null);
  }

  public DocumentContentImpl(@Nullable Project project,
                             @NotNull Document document,
                             @Nullable FileType type,
                             @Nullable VirtualFile highlightFile,
                             @Nullable LineSeparator separator,
                             @Nullable Charset charset,
                             @Nullable Boolean bom) {
    myProject = project;
    myDocument = document;
    myType = type;
    myHighlightFile = highlightFile;
    mySeparator = separator;
    myCharset = charset;
    myBOM = bom;
  }

  @Nullable
  public Project getProject() {
    return myProject;
  }

  @NotNull
  @Override
  public Document getDocument() {
    return myDocument;
  }

  @Nullable
  @Override
  public VirtualFile getHighlightFile() {
    return myHighlightFile;
  }

  @Nullable
  @Override
  public Navigatable getNavigatable(@NotNull LineCol position) {
    if (!DiffUtil.canNavigateToFile(myProject, getHighlightFile())) return null;
    return new MyNavigatable(myProject, getHighlightFile(), getDocument(), position);
  }

  @Nullable
  @Override
  public Navigatable getNavigatable() {
    return getNavigatable(new LineCol(0));
  }

  @Nullable
  @Override
  public LineSeparator getLineSeparator() {
    return mySeparator;
  }

  @Override
  @Nullable
  public Boolean hasBom() {
    return myBOM;
  }

  @Nullable
  @Override
  public FileType getContentType() {
    return myType;
  }

  @Nullable
  @Override
  public Charset getCharset() {
    return myCharset;
  }


  private static class MyNavigatable implements Navigatable {
    @NotNull private final Project myProject;
    @NotNull private final VirtualFile myTargetFile;
    @NotNull private final Document myDocument;
    @NotNull private final LineCol myPosition;

    public MyNavigatable(@NotNull Project project, @NotNull VirtualFile targetFile, @NotNull Document document, @NotNull LineCol position) {
      myProject = project;
      myTargetFile = targetFile;
      myDocument = document;
      myPosition = position;
    }

    @Override
    public void navigate(boolean requestFocus) {
      Document targetDocument = FileDocumentManager.getInstance().getDocument(myTargetFile);
      LineCol targetPosition = translatePosition(myDocument, targetDocument, myPosition);
      OpenFileDescriptor descriptor = targetDocument != null
                                      ? new OpenFileDescriptor(myProject, myTargetFile, targetPosition.toOffset(targetDocument))
                                      : new OpenFileDescriptor(myProject, myTargetFile, targetPosition.line, targetPosition.column);
      if (descriptor.canNavigate()) descriptor.navigate(true);
    }

    @Override
    public boolean canNavigate() {
      return myTargetFile.isValid();
    }

    @Override
    public boolean canNavigateToSource() {
      return false;
    }

    @NotNull
    private static LineCol translatePosition(@NotNull Document fromDocument, @Nullable Document toDocument, @NotNull LineCol position) {
      try {
        if (toDocument == null) return position;
        int targetLine = Diff.translateLine(fromDocument.getCharsSequence(), toDocument.getCharsSequence(), position.line, true);
        return new LineCol(targetLine, position.column);
      }
      catch (FilesTooBigForDiffException ignore) {
        return position;
      }
    }
  }
}

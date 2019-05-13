/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.history.integration.ui.models;

import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.history.core.revisions.Revision;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;

public class SelectionDifferenceModel extends FileDifferenceModel {
  private final SelectionCalculator myCalculator;
  private final Revision myLeftRevision;
  private final Revision myRightRevision;
  private final int myFrom;
  private final int myTo;

  public SelectionDifferenceModel(Project p,
                                  IdeaGateway gw,
                                  SelectionCalculator c,
                                  Revision left,
                                  Revision right,
                                  int from,
                                  int to,
                                  boolean editableRightContent) {
    super(p, gw, editableRightContent);
    myCalculator = c;
    myLeftRevision = left;
    myRightRevision = right;
    myFrom = from;
    myTo = to;
  }

  @Override
  protected Entry getLeftEntry() {
    return myLeftRevision.findEntry();
  }

  @Override
  protected Entry getRightEntry() {
    return myRightRevision.findEntry();
  }

  @Override
  protected boolean isLeftContentAvailable(RevisionProcessingProgress p) {
    return myCalculator.canCalculateFor(myLeftRevision, p);
  }

  @Override
  protected boolean isRightContentAvailable(RevisionProcessingProgress p) {
    return myCalculator.canCalculateFor(myRightRevision, p);
  }

  @Override
  protected DiffContent doGetLeftDiffContent(RevisionProcessingProgress p) {
    return getDiffContent(myLeftRevision, p);
  }

  @Override
  protected DiffContent getReadOnlyRightDiffContent(RevisionProcessingProgress p) {
    return getDiffContent(myRightRevision, p);
  }

  @Override
  protected DiffContent getEditableRightDiffContent(RevisionProcessingProgress p) {
    Document d = getDocument();

    int fromOffset = d.getLineStartOffset(myFrom);
    int toOffset = d.getLineEndOffset(myTo);

    return DiffContentFactory.getInstance().createFragment(myProject, d, new TextRange(fromOffset, toOffset));
  }

  private DocumentContent getDiffContent(Revision r, RevisionProcessingProgress p) {
    Entry e = r.findEntry();
    String content = myCalculator.getSelectionFor(r, p).getBlockContent();
    VirtualFile virtualFile = myGateway.findVirtualFile(e.getPath());
    if (virtualFile != null) {
      return DiffContentFactory.getInstance().create(content, virtualFile);
    }
    else {
      FileType fileType = myGateway.getFileType(e.getName());
      return DiffContentFactory.getInstance().create(content, fileType);
    }
  }
}

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
import com.intellij.history.core.tree.Entry;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;

public class EntireFileDifferenceModel extends FileDifferenceModel {
  private final Entry myLeft;
  private final Entry myRight;

  public EntireFileDifferenceModel(Project p, IdeaGateway gw, Entry left, Entry right, boolean editableRightContent) {
    super(p, gw, editableRightContent);
    myLeft = left;
    myRight = right;
  }

  @Override
  protected Entry getLeftEntry() {
    return myLeft;
  }

  @Override
  protected Entry getRightEntry() {
    return myRight;
  }

  @Override
  protected boolean isLeftContentAvailable(RevisionProcessingProgress p) {
    return myLeft.getContent().isAvailable();
  }

  @Override
  protected boolean isRightContentAvailable(RevisionProcessingProgress p) {
    return myRight.getContent().isAvailable();
  }

  @Override
  protected DiffContent doGetLeftDiffContent(RevisionProcessingProgress p) {
    return getDiffContent(myLeft);
  }

  @Override
  protected DiffContent getReadOnlyRightDiffContent(RevisionProcessingProgress p) {
    return getDiffContent(myRight);
  }

  @Override
  protected DiffContent getEditableRightDiffContent(RevisionProcessingProgress p) {
    Document d = getDocument();
    return DiffContentFactory.getInstance().create(myProject, d);
  }

  private DocumentContent getDiffContent(Entry e) {
    return createSimpleDiffContent(getContentOf(e), e);
  }

  private String getContentOf(Entry e) {
    return e.getContent().getString(e, myGateway);
  }
}

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

import com.intellij.history.core.tree.Entry;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.history.integration.LocalHistoryBundle;
import com.intellij.openapi.diff.DiffContent;
import com.intellij.openapi.diff.SimpleContent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.text.DateFormatUtil;

public abstract class FileDifferenceModel {
  protected final Project myProject;
  protected final IdeaGateway myGateway;
  private final boolean isRightContentCurrent;

  protected FileDifferenceModel(Project p, IdeaGateway gw, boolean currentRightContent) {
    myProject = p;
    myGateway = gw;
    isRightContentCurrent = currentRightContent;
  }

  public String getTitle() {
    Entry e = getRightEntry();
    if (e == null) e = getLeftEntry();
    return FileUtil.toSystemDependentName(e.getPath());
  }

  public String getLeftTitle(RevisionProcessingProgress p) {
    if (!hasLeftEntry()) return LocalHistoryBundle.message("file.does.not.exist");
    return formatTitle(getLeftEntry(), isLeftContentAvailable(p));
  }

  public String getRightTitle(RevisionProcessingProgress p) {
    if (!hasRightEntry()) return LocalHistoryBundle.message("file.does.not.exist"); 
    if (!isRightContentAvailable(p)) {
      return formatTitle(getRightEntry(), false);
    }
    if (isRightContentCurrent) return LocalHistoryBundle.message("current.revision");
    return formatTitle(getRightEntry(), true);
  }

  private String formatTitle(Entry e, boolean isAvailable) {
    String result = DateFormatUtil.formatPrettyDateTime(e.getTimestamp()) + " - " + e.getName();
    if (!isAvailable) {
      result += " - " + LocalHistoryBundle.message("content.not.available");
    }
    return result;
  }

  protected abstract Entry getLeftEntry();

  protected abstract Entry getRightEntry();

  public DiffContent getLeftDiffContent(RevisionProcessingProgress p) {
    if (!canShowLeftEntry(p)) return new SimpleContent("");
    return doGetLeftDiffContent(p);
  }

  protected abstract DiffContent doGetLeftDiffContent(RevisionProcessingProgress p);

  public DiffContent getRightDiffContent(RevisionProcessingProgress p) {
    if (!canShowRightEntry(p)) return new SimpleContent("");
    if (isRightContentCurrent) return getEditableRightDiffContent(p);
    return getReadOnlyRightDiffContent(p);
  }

  private boolean canShowLeftEntry(RevisionProcessingProgress p) {
    return hasLeftEntry() && isLeftContentAvailable(p);
  }

  private boolean canShowRightEntry(RevisionProcessingProgress p) {
    return hasRightEntry() && isRightContentAvailable(p);
  }

  private boolean hasLeftEntry() {
    return getLeftEntry() != null;
  }

  private boolean hasRightEntry() {
    return getRightEntry() != null;
  }

  protected abstract boolean isLeftContentAvailable(RevisionProcessingProgress p);

  protected abstract boolean isRightContentAvailable(RevisionProcessingProgress p);

  protected abstract DiffContent getReadOnlyRightDiffContent(RevisionProcessingProgress p);

  protected abstract DiffContent getEditableRightDiffContent(RevisionProcessingProgress p);

  protected SimpleContent createSimpleDiffContent(String content, Entry e) {
    return new SimpleContent(content, myGateway.getFileType(e.getName()));
  }

  protected Document getDocument() {
    return myGateway.getDocument(getRightEntry().getPath());
  }
}

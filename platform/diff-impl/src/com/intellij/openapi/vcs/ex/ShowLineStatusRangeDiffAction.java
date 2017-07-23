/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.ex;

import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.DiffManager;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.diff.util.DiffUtil.getLineCount;

public class ShowLineStatusRangeDiffAction extends DumbAwareAction {
  private final LineStatusTrackerBase myLineStatusTracker;
  private final Range myRange;

  public ShowLineStatusRangeDiffAction(@NotNull LineStatusTrackerBase lineStatusTracker, @NotNull Range range, @Nullable Editor editor) {
    myLineStatusTracker = lineStatusTracker;
    myRange = range;
    ActionUtil.copyFrom(this, IdeActions.ACTION_SHOW_DIFF_COMMON);
  }

  public void update(final AnActionEvent e) {
    e.getPresentation().setEnabled(myLineStatusTracker.isValid());
  }

  @Override
  public void actionPerformed(final AnActionEvent e) {
    DiffManager.getInstance().showDiff(e.getProject(), createDiffData());
  }

  @NotNull
  private DiffRequest createDiffData() {
    Range range = expand(myRange, myLineStatusTracker.getDocument(), myLineStatusTracker.getVcsDocument());

    DiffContent vcsContent = createDiffContent(myLineStatusTracker.getVcsDocument(),
                                               myLineStatusTracker.getVirtualFile(),
                                               myLineStatusTracker.getVcsTextRange(range));
    DiffContent currentContent = createDiffContent(myLineStatusTracker.getDocument(),
                                                   myLineStatusTracker.getVirtualFile(),
                                                   myLineStatusTracker.getCurrentTextRange(range));

    return new SimpleDiffRequest(VcsBundle.message("dialog.title.diff.for.range"),
                                 vcsContent, currentContent,
                                 VcsBundle.message("diff.content.title.up.to.date"),
                                 VcsBundle.message("diff.content.title.current.range")
    );
  }

  @NotNull
  private DiffContent createDiffContent(@NotNull Document document, @Nullable VirtualFile highlightFile, @NotNull TextRange textRange) {
    final Project project = myLineStatusTracker.getProject();
    DocumentContent content = DiffContentFactory.getInstance().create(project, document, highlightFile);
    return DiffContentFactory.getInstance().createFragment(project, content, textRange);
  }

  @NotNull
  private static Range expand(@NotNull Range range, @NotNull Document document, @NotNull Document uDocument) {
    boolean canExpandBefore = range.getLine1() != 0 && range.getVcsLine1() != 0;
    boolean canExpandAfter = range.getLine2() < getLineCount(document) && range.getVcsLine2() < getLineCount(uDocument);
    int offset1 = range.getLine1() - (canExpandBefore ? 1 : 0);
    int uOffset1 = range.getVcsLine1() - (canExpandBefore ? 1 : 0);
    int offset2 = range.getLine2() + (canExpandAfter ? 1 : 0);
    int uOffset2 = range.getVcsLine2() + (canExpandAfter ? 1 : 0);
    return new Range(offset1, offset2, uOffset1, uOffset2);
  }
}
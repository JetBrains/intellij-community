// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.history.integration.revertion;

import com.intellij.diff.Block;
import com.intellij.history.core.LocalHistoryFacade;
import com.intellij.history.core.revisions.Revision;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.history.integration.ui.models.Progress;
import com.intellij.history.integration.ui.models.RevisionDataKt;
import com.intellij.history.integration.ui.models.SelectionCalculator;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.lvcs.impl.RevisionId;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

@ApiStatus.Internal
public final class SelectionReverter extends Reverter {
  private final SelectionCalculator myCalculator;
  private final @NotNull RevisionId myTargetRevisionId;
  private final @NlsSafe String myTargetPath;
  private final int myFromLine;
  private final int myToLine;

  public SelectionReverter(Project p,
                           LocalHistoryFacade vcs,
                           IdeaGateway gw,
                           SelectionCalculator c,
                           Revision targetRevision,
                           Entry rightEntry,
                           int fromLine,
                           int toLine) {
    this(p, vcs, gw, c, RevisionDataKt.toRevisionId(targetRevision), rightEntry.getPath(), fromLine, toLine,
         () -> Reverter.getRevertCommandName(targetRevision));
  }

  public SelectionReverter(Project project,
                           LocalHistoryFacade facade,
                           IdeaGateway gateway,
                           SelectionCalculator calculator,
                           @NotNull RevisionId targetRevisionId,
                           String targetPath,
                           int fromLine,
                           int toLine,
                           @NotNull Supplier<@NlsContexts.Command String> commandName) {
    super(project, facade, gateway, commandName);
    myCalculator = calculator;
    myTargetRevisionId = targetRevisionId;
    myTargetPath = targetPath;
    myFromLine = fromLine;
    myToLine = toLine;
  }

  @Override
  protected @NotNull List<VirtualFile> getFilesToClearROStatus() {
    return Collections.singletonList(myGateway.findVirtualFile(myTargetPath));
  }

  @Override
  protected void doRevert() {
    Block b = myCalculator.getSelectionFor(myTargetRevisionId, Progress.EMPTY);

    Document d = myGateway.getDocument(myTargetPath);

    int from = d.getLineStartOffset(myFromLine);
    int to = d.getLineEndOffset(myToLine);

    d.replaceString(from, to, b.getBlockContent());
  }
}

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
package com.intellij.diff.requests;

import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.contents.FileContent;
import com.intellij.diff.merge.BinaryMergeRequest;
import com.intellij.diff.merge.MergeCallback;
import com.intellij.diff.merge.MergeResult;
import com.intellij.diff.merge.MergeUtil;
import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.ThreeSide;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.UIBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;

public class BinaryMergeRequestImpl extends BinaryMergeRequest {
  private static final Logger LOG = Logger.getInstance(BinaryMergeRequestImpl.class);

  @Nullable private final Project myProject;
  @NotNull private final FileContent myFile;
  @NotNull private final List<DiffContent> myContents;

  @NotNull private final List<byte[]> myByteContents;
  private final byte @NotNull [] myOriginalContent;

  @Nullable private final @NlsContexts.DialogTitle String myTitle;
  @NotNull private final List<String> myTitles;

  public BinaryMergeRequestImpl(@Nullable Project project,
                                @NotNull FileContent file,
                                byte @NotNull [] originalContent,
                                @NotNull List<DiffContent> contents,
                                @NotNull List<byte[]> byteContents,
                                @Nullable @NlsContexts.DialogTitle String title,
                                @NotNull List<@Nls String> contentTitles) {
    assert byteContents.size() == 3;
    assert contents.size() == 3;
    assert contentTitles.size() == 3;

    myProject = project;
    myFile = file;
    myOriginalContent = originalContent;

    myByteContents = byteContents;
    myContents = contents;
    myTitle = title;
    myTitles = contentTitles;
  }

  @NotNull
  @Override
  public FileContent getOutputContent() {
    return myFile;
  }

  @NotNull
  @Override
  public List<DiffContent> getContents() {
    return myContents;
  }

  @NotNull
  @Override
  public List<byte[]> getByteContents() {
    return myByteContents;
  }

  @Nullable
  @Override
  public String getTitle() {
    return myTitle;
  }

  @NotNull
  @Override
  public List<String> getContentTitles() {
    return myTitles;
  }

  @Override
  public void applyResult(@NotNull MergeResult result) {
    final byte[] applyContent = switch (result) {
      case CANCEL -> MergeUtil.shouldRestoreOriginalContentOnCancel(this) ? myOriginalContent : null;
      case LEFT -> ThreeSide.LEFT.select(myByteContents);
      case RIGHT -> ThreeSide.RIGHT.select(myByteContents);
      case RESOLVED -> null;
    };

    if (applyContent != null) {
      try {
        VirtualFile file = myFile.getFile();
        if (!file.isValid()) {
          throw new IOException(IdeBundle.message("error.file.not.found.message", file.getPresentableUrl()));
        }
        if (!DiffUtil.makeWritable(myProject, file)) {
          throw new IOException(UIBundle.message("file.is.read.only.message.text", file.getPresentableUrl()));
        }

        WriteCommandAction.writeCommandAction(null).run(() -> {
          file.setBinaryContent(applyContent);
        });
      }
      catch (IOException e) {
        LOG.warn(e);
        Messages.showErrorDialog(myProject, e.getMessage(), DiffBundle.message("can.t.finish.merge.resolve"));
      }
    }

    MergeCallback.getCallback(this).applyResult(result);
  }

  @Override
  public void onAssigned(boolean assigned) {
    myFile.onAssigned(assigned);
    for (DiffContent content : myContents) {
      content.onAssigned(assigned);
    }
  }
}

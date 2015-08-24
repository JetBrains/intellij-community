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
package com.intellij.diff.tools;

import com.intellij.diff.DiffContext;
import com.intellij.diff.DiffContextEx;
import com.intellij.diff.FrameDiffTool;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.contents.FileContent;
import com.intellij.diff.requests.*;
import com.intellij.diff.util.DiffUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public class ErrorDiffTool implements FrameDiffTool {
  public static final ErrorDiffTool INSTANCE = new ErrorDiffTool();

  @NotNull
  @Override
  public DiffViewer createComponent(@NotNull DiffContext context, @NotNull DiffRequest request) {
    return new MyViewer(context, request);
  }

  @Override
  public boolean canShow(@NotNull DiffContext context, @NotNull DiffRequest request) {
    return true;
  }

  @NotNull
  @Override
  public String getName() {
    return "Error viewer";
  }

  private static class MyViewer implements DiffViewer {
    @NotNull private final DiffContext myContext;
    @NotNull private final DiffRequest myRequest;

    @NotNull private final JPanel myPanel;

    public MyViewer(@NotNull DiffContext context, @NotNull DiffRequest request) {
      myContext = context;
      myRequest = request;

      myPanel = JBUI.Panels.simplePanel(createComponent(request));
    }

    @NotNull
    private JComponent createComponent(@NotNull DiffRequest request) {
      if (request instanceof MessageDiffRequest) {
        // TODO: explain some of ErrorDiffRequest exceptions ?
        String message = ((MessageDiffRequest)request).getMessage();
        return DiffUtil.createMessagePanel(message);
      }
      if (request instanceof ComponentDiffRequest) {
        return ((ComponentDiffRequest)request).getComponent(myContext);
      }
      if (request instanceof ContentDiffRequest) {
        List<DiffContent> contents = ((ContentDiffRequest)request).getContents();
        for (final DiffContent content : contents) {
          if (content instanceof FileContent && FileTypes.UNKNOWN.equals(content.getContentType())) {
            final VirtualFile file = ((FileContent)content).getFile();

            UnknownFileTypeDiffRequest unknownFileTypeRequest = new UnknownFileTypeDiffRequest(file, myRequest.getTitle());
            return unknownFileTypeRequest.getComponent(myContext);
          }
        }
      }

      return DiffUtil.createMessagePanel("Can't show diff");
    }

    @NotNull
    @Override
    public JComponent getComponent() {
      return myPanel;
    }

    @Nullable
    @Override
    public JComponent getPreferredFocusedComponent() {
      return null;
    }

    @NotNull
    @Override
    public ToolbarComponents init() {
      if (myRequest instanceof UnknownFileTypeDiffRequest) {
        String fileName = ((UnknownFileTypeDiffRequest)myRequest).getFileName();
        if (fileName != null && FileTypeManager.getInstance().getFileTypeByFileName(fileName) != UnknownFileType.INSTANCE) {
          // FileType was assigned elsewhere (ex: by other UnknownFileTypeDiffRequest). We should reload request.
          if (myContext instanceof DiffContextEx) {
            ApplicationManager.getApplication().invokeLater(new Runnable() {
              @Override
              public void run() {
                ((DiffContextEx)myContext).reloadDiffRequest();
              }
            }, ModalityState.current());
          }
        }
      }

      return new ToolbarComponents();
    }

    @Override
    public void dispose() {
    }
  }
}

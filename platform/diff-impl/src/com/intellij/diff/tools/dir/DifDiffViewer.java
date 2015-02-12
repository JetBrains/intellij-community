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
package com.intellij.diff.tools.dir;

import com.intellij.diff.DiffContext;
import com.intellij.diff.FrameDiffTool;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.contents.DirectoryContent;
import com.intellij.diff.contents.FileContent;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.ide.diff.DiffElement;
import com.intellij.ide.diff.DirDiffSettings;
import com.intellij.ide.diff.JarFileDiffElement;
import com.intellij.ide.diff.VirtualFileDiffElement;
import com.intellij.ide.highlighter.ArchiveFileType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diff.impl.dir.DirDiffFrame;
import com.intellij.openapi.diff.impl.dir.DirDiffPanel;
import com.intellij.openapi.diff.impl.dir.DirDiffTableModel;
import com.intellij.openapi.diff.impl.dir.DirDiffWindow;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

class DifDiffViewer implements FrameDiffTool.DiffViewer {
  @NotNull private final DiffContext myContext;
  @NotNull private final ContentDiffRequest myRequest;

  @NotNull private final DirDiffPanel myPanel;

  public DifDiffViewer(@NotNull DiffContext context, @NotNull ContentDiffRequest request) {
    myContext = context;
    myRequest = request;

    List<DiffContent> contents = request.getContents();
    DiffElement element1 = createDiffElement(contents.get(0));
    DiffElement element2 = createDiffElement(contents.get(1));
    DirDiffTableModel model = new DirDiffTableModel(context.getProject(), element1, element2, new DirDiffSettings());

    myPanel = new DirDiffPanel(model, new DirDiffWindow((DirDiffFrame)null) {
      @Override
      public Window getWindow() {
        return null;
      }

      @Override
      public Disposable getDisposable() {
        return DifDiffViewer.this;
      }

      @Override
      public void setTitle(String title) {
      }
    });
  }

  @NotNull
  @Override
  public FrameDiffTool.ToolbarComponents init() {
    myPanel.setupSplitter();

    return new FrameDiffTool.ToolbarComponents();
  }

  @Override
  public void dispose() {
    Disposer.dispose(myPanel);
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myPanel.getPanel();
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myPanel.getTable();
  }

  //
  // Misc
  //

  public static boolean canShowRequest(@NotNull DiffContext context, @NotNull DiffRequest request) {
    if (!(request instanceof ContentDiffRequest)) return false;
    List<DiffContent> contents = ((ContentDiffRequest)request).getContents();
    if (contents.size() != 2) return false;

    if (!canShowContent(contents.get(0))) return false;
    if (!canShowContent(contents.get(1))) return false;

    return true;
  }

  private static boolean canShowContent(@NotNull DiffContent content) {
    if (content instanceof DirectoryContent) return true;
    if (content instanceof FileContent &&
        content.getContentType() instanceof ArchiveFileType &&
        ((FileContent)content).getFile().isInLocalFileSystem()) {
      return true;
    }

    return false;
  }

  @NotNull
  private static DiffElement createDiffElement(@NotNull DiffContent content) {
    if (content instanceof DirectoryContent) {
      return new VirtualFileDiffElement(((DirectoryContent)content).getFile());
    }
    if (content instanceof FileContent && content.getContentType() instanceof ArchiveFileType) {
      return new JarFileDiffElement(((FileContent)content).getFile());
    }
    throw new IllegalArgumentException(content.getClass() + " " + content.getContentType());
  }
}

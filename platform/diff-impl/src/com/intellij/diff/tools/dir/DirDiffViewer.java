// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.dir;

import com.intellij.diff.DiffContext;
import com.intellij.diff.DiffContextEx;
import com.intellij.diff.FrameDiffTool;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.contents.DirectoryContent;
import com.intellij.diff.contents.EmptyContent;
import com.intellij.diff.contents.FileContent;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.ide.diff.DiffElement;
import com.intellij.ide.diff.DirDiffSettings;
import com.intellij.ide.diff.VirtualFileDiffElement;
import com.intellij.ide.highlighter.ArchiveFileType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.actionSystem.UiDataProvider;
import com.intellij.openapi.diff.impl.dir.DirDiffPanel;
import com.intellij.openapi.diff.impl.dir.DirDiffTableModel;
import com.intellij.openapi.diff.impl.dir.DirDiffWindow;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.List;

class DirDiffViewer implements FrameDiffTool.DiffViewer {

  private final DirDiffPanel myDirDiffPanel;
  private final JComponent myComponent;
  private final String myHelpID;

  DirDiffViewer(@NotNull DiffContext context, @NotNull ContentDiffRequest request) {
    this(context,
         createDiffElement(request.getContents().get(0)),
         createDiffElement(request.getContents().get(1)),
         ObjectUtils.notNull(context.getUserData(DirDiffSettings.KEY), new DirDiffSettings()),
         "reference.dialogs.diff.folder");
  }

  DirDiffViewer(@NotNull DiffContext context,
                @NotNull DiffElement element1,
                @NotNull DiffElement element2,
                @NotNull DirDiffSettings settings,
                @Nullable @NonNls String helpID) {
    myHelpID = helpID;

    DirDiffTableModel model = new DirDiffTableModel(context.getProject(), element1, element2, settings);

    myDirDiffPanel = new DirDiffPanel(model, new DirDiffWindow() {
      @Override
      public @NotNull Disposable getDisposable() {
        return DirDiffViewer.this;
      }

      @Override
      public void setTitle(@NotNull String title) {
        if (context instanceof DiffContextEx) ((DiffContextEx)context).setWindowTitle(title);
      }
    });

    myComponent = UiDataProvider.wrapComponent(myDirDiffPanel.getPanel(), sink -> {
      sink.set(PlatformCoreDataKeys.HELP_ID, myHelpID);
      DataSink.uiDataSnapshot(sink, myDirDiffPanel);
    });
  }

  @Override
  public @NotNull FrameDiffTool.ToolbarComponents init() {
    FrameDiffTool.ToolbarComponents components = new FrameDiffTool.ToolbarComponents();
    components.toolbarActions = Arrays.asList(myDirDiffPanel.getActions());
    components.statusPanel = myDirDiffPanel.extractFilterPanel();
    return components;
  }

  @Override
  public void dispose() {
    Disposer.dispose(myDirDiffPanel);
  }

  @Override
  public @NotNull JComponent getComponent() {
    return myComponent;
  }

  @Override
  public @Nullable JComponent getPreferredFocusedComponent() {
    return myDirDiffPanel.getTable();
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

    if (contents.get(0) instanceof EmptyContent && contents.get(1) instanceof EmptyContent) return false;

    return true;
  }

  private static boolean canShowContent(@NotNull DiffContent content) {
    if (content instanceof EmptyContent) return true;
    if (content instanceof DirectoryContent) return true;
    if (content instanceof FileContent &&
        content.getContentType() instanceof ArchiveFileType &&
        ((FileContent)content).getFile().isValid() &&
        ((FileContent)content).getFile().isInLocalFileSystem()) {
      return true;
    }

    return false;
  }

  private static @NotNull DiffElement createDiffElement(@NotNull DiffContent content) {
    if (content instanceof EmptyContent) {
      return new DiffElement() {
        @Override
        public String getPath() {
          return "";
        }

        @Override
        public @NotNull String getName() {
          return "Nothing";
        }

        @Override
        public long getSize() {
          return -1;
        }

        @Override
        public long getTimeStamp() {
          return -1;
        }

        @Override
        public boolean isContainer() {
          return true;
        }

        @Override
        public DiffElement[] getChildren() {
          return EMPTY_ARRAY;
        }

        @Override
        public byte[] getContent() {
          return null;
        }

        @Override
        public Object getValue() {
          return null;
        }
      };
    }
    if (content instanceof DirectoryContent) {
      VirtualFile file = ((DirectoryContent)content).getFile();
      return VirtualFileDiffElement.createElement(file, file);
    }
    if (content instanceof FileContent && content.getContentType() instanceof ArchiveFileType) {
      VirtualFile file = ((FileContent)content).getFile();
      return VirtualFileDiffElement.createElement(file, file);
    }
    throw new IllegalArgumentException(content.getClass() + " " + content.getContentType());
  }
}
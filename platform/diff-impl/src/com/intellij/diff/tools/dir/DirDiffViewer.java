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
import com.intellij.diff.contents.EmptyContent;
import com.intellij.diff.contents.FileContent;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.ide.DataManager;
import com.intellij.ide.diff.DiffElement;
import com.intellij.ide.diff.DirDiffSettings;
import com.intellij.ide.diff.JarFileDiffElement;
import com.intellij.ide.diff.VirtualFileDiffElement;
import com.intellij.ide.highlighter.ArchiveFileType;
import com.intellij.internal.statistic.UsageTrigger;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.diff.impl.dir.DirDiffPanel;
import com.intellij.openapi.diff.impl.dir.DirDiffTableModel;
import com.intellij.openapi.diff.impl.dir.DirDiffWindow;
import com.intellij.openapi.project.DefaultProjectFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

class DirDiffViewer implements FrameDiffTool.DiffViewer {
  @NotNull private final DiffContext myContext;
  @NotNull private final ContentDiffRequest myRequest;

  @NotNull private final DirDiffPanel myDirDiffPanel;
  @NotNull private final JPanel myPanel;

  public DirDiffViewer(@NotNull DiffContext context, @NotNull ContentDiffRequest request) {
    UsageTrigger.trigger("diff.DirDiffViewer");

    myContext = context;
    myRequest = request;

    List<DiffContent> contents = request.getContents();
    DiffElement element1 = createDiffElement(contents.get(0));
    DiffElement element2 = createDiffElement(contents.get(1));

    Project project = context.getProject();
    if (project == null) project = DefaultProjectFactory.getInstance().getDefaultProject();

    DirDiffSettings settings = ObjectUtils.notNull(context.getUserData(DirDiffSettings.KEY), new DirDiffSettings());
    DirDiffTableModel model = new DirDiffTableModel(project, element1, element2, settings);

    myDirDiffPanel = new DirDiffPanel(model, new DirDiffWindow() {
      @NotNull
      @Override
      public Disposable getDisposable() {
        return DirDiffViewer.this;
      }

      @Override
      public void setTitle(@NotNull String title) {
      }
    });

    myPanel = new JPanel(new BorderLayout());
    myPanel.add(myDirDiffPanel.getPanel(), BorderLayout.CENTER);
    DataManager.registerDataProvider(myPanel, dataId -> {
      if (PlatformDataKeys.HELP_ID.is(dataId)) {
        return "reference.dialogs.diff.folder";
      }
      return myDirDiffPanel.getData(dataId);
    });
  }

  @NotNull
  @Override
  public FrameDiffTool.ToolbarComponents init() {
    myDirDiffPanel.setupSplitter();

    FrameDiffTool.ToolbarComponents components = new FrameDiffTool.ToolbarComponents();
    // we return ActionGroup to avoid registering of actions shortcuts
    // * they are already registered inside DirDiffPanel
    // * this fixes conflict between FilterPanel and SynchronizeDiff action for the 'Enter' shortcut
    components.toolbarActions =  Collections.singletonList(new DefaultActionGroup(myDirDiffPanel.getActions()));
    components.statusPanel = myDirDiffPanel.extractFilterPanel();
    return components;
  }

  @Override
  public void dispose() {
    Disposer.dispose(myDirDiffPanel);
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myPanel;
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
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

  @NotNull
  private static DiffElement createDiffElement(@NotNull DiffContent content) {
    if (content instanceof EmptyContent) {
      return new DiffElement() {
        @Override
        public String getPath() {
          return "";
        }

        @NotNull
        @Override
        public String getName() {
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
        public DiffElement[] getChildren() throws IOException {
          return EMPTY_ARRAY;
        }

        @Override
        public byte[] getContent() throws IOException {
          return null;
        }

        @Override
        public Object getValue() {
          return null;
        }
      };
    }
    if (content instanceof DirectoryContent) {
      return new VirtualFileDiffElement(((DirectoryContent)content).getFile());
    }
    if (content instanceof FileContent && content.getContentType() instanceof ArchiveFileType) {
      return new JarFileDiffElement(((FileContent)content).getFile());
    }
    throw new IllegalArgumentException(content.getClass() + " " + content.getContentType());
  }
}

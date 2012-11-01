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

package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.EventListener;

/**
 * @author Eugene Zhuravlev
 * @since Oct 8, 2003
 */
@SuppressWarnings("UnusedDeclaration")
public abstract class ContentEntryEditor implements ContentRootPanel.ActionCallback {
  private boolean myIsSelected;
  private ContentRootPanel myContentRootPanel;
  private JPanel myMainPanel;
  protected EventDispatcher<ContentEntryEditorListener> myEventDispatcher;
  private final String myContentEntryUrl;
  protected final boolean myCanMarkSources;
  protected final boolean myCanMarkTestSources;

  public interface ContentEntryEditorListener extends EventListener{
    void editingStarted(@NotNull ContentEntryEditor editor);
    void beforeEntryDeleted(@NotNull ContentEntryEditor editor);
    void sourceFolderAdded(@NotNull ContentEntryEditor editor, SourceFolder folder);
    void sourceFolderRemoved(@NotNull ContentEntryEditor editor, VirtualFile file, boolean isTestSource);
    void folderExcluded(@NotNull ContentEntryEditor editor, VirtualFile file);
    void folderIncluded(@NotNull ContentEntryEditor editor, VirtualFile file);
    void navigationRequested(@NotNull ContentEntryEditor editor, VirtualFile file);
    void packagePrefixSet(@NotNull ContentEntryEditor editor, @NotNull SourceFolder folder);
  }

  public ContentEntryEditor(final String contentEntryUrl, boolean canMarkSources, boolean canMarkTestSources) {
    myContentEntryUrl = contentEntryUrl;
    myCanMarkSources = canMarkSources;
    myCanMarkTestSources = canMarkTestSources;
  }

  public String getContentEntryUrl() {
    return myContentEntryUrl;
  }

  public void initUI() {
    myMainPanel = new JPanel(new BorderLayout());
    myMainPanel.setOpaque(false);
    myMainPanel.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        myEventDispatcher.getMulticaster().editingStarted(ContentEntryEditor.this);
      }
      @Override
      public void mouseEntered(MouseEvent e) {
        if (!myIsSelected) {
          highlight(true);
        }
      }
      @Override
      public void mouseExited(MouseEvent e) {
        if (!myIsSelected) {
          highlight(false);
        }
      }
    });
    myEventDispatcher = EventDispatcher.create(ContentEntryEditorListener.class);
    setSelected(false);
    update();
  }

  @Nullable
  protected ContentEntry getContentEntry() {
    final ModifiableRootModel model = getModel();
    if (model != null) {
      final ContentEntry[] entries = model.getContentEntries();
      for (ContentEntry entry : entries) {
        if (entry.getUrl().equals(myContentEntryUrl)) return entry;
      }
    }

    return null;
  }

  protected abstract ModifiableRootModel getModel();

  @Override
  public void deleteContentEntry() {
    final String path = FileUtil.toSystemDependentName(VfsUtilCore.urlToPath(myContentEntryUrl));
    final int answer = Messages.showYesNoDialog(ProjectBundle.message("module.paths.remove.content.prompt", path),
                                                ProjectBundle.message("module.paths.remove.content.title"), Messages.getQuestionIcon());
    if (answer != 0) { // no
      return;
    }
    myEventDispatcher.getMulticaster().beforeEntryDeleted(this);
    final ContentEntry entry = getContentEntry();
    if (entry != null) {
      getModel().removeContentEntry(entry);
    }
  }

  @Override
  public void deleteContentFolder(ContentEntry contentEntry, ContentFolder folder) {
    if (folder instanceof SourceFolder) {
      removeSourceFolder((SourceFolder)folder);
      update();
    }
    else if (folder instanceof ExcludeFolder) {
      removeExcludeFolder((ExcludeFolder)folder);
      update();
    }

  }

  @Override
  public void navigateFolder(ContentEntry contentEntry, ContentFolder contentFolder) {
    final VirtualFile file = contentFolder.getFile();
    if (file != null) { // file can be deleted externally
      myEventDispatcher.getMulticaster().navigationRequested(this, file);
    }
  }

  @Override
  public void setPackagePrefix(@NotNull SourceFolder folder, @NotNull String prefix) {
    folder.setPackagePrefix(prefix);
    update();
    myEventDispatcher.getMulticaster().packagePrefixSet(this, folder);
  }

  public void addContentEntryEditorListener(ContentEntryEditorListener listener) {
    myEventDispatcher.addListener(listener);
  }

  public void removeContentEntryEditorListener(ContentEntryEditorListener listener) {
    myEventDispatcher.removeListener(listener);
  }

  public void setSelected(boolean isSelected) {
    if (myIsSelected != isSelected) {
      highlight(isSelected);
      myIsSelected = isSelected;
    }
  }

  private void highlight(boolean selected) {
    if (myContentRootPanel != null) {
      myContentRootPanel.setSelected(selected);
    }
  }

  public JComponent getComponent() {
    return myMainPanel;
  }

  public void update() {
    if (myContentRootPanel != null) {
      myMainPanel.remove(myContentRootPanel);
    }
    myContentRootPanel = createContentRootPane();
    myContentRootPanel.initUI();
    myContentRootPanel.setSelected(myIsSelected);
    myMainPanel.add(myContentRootPanel, BorderLayout.CENTER);
    myMainPanel.revalidate();
  }

  protected ContentRootPanel createContentRootPane() {
    return new ContentRootPanel(this, myCanMarkSources, myCanMarkTestSources) {
      @Override
      protected ContentEntry getContentEntry() {
        return ContentEntryEditor.this.getContentEntry();
      }
    };
  }

  @Nullable
  public SourceFolder addSourceFolder(@NotNull final VirtualFile file, boolean isTestSource, String packagePrefix) {
    final ContentEntry contentEntry = getContentEntry();
    if (contentEntry != null) {
      final SourceFolder sourceFolder = contentEntry.addSourceFolder(file, isTestSource, packagePrefix);
      try {
        return sourceFolder;
      }
      finally {
        myEventDispatcher.getMulticaster().sourceFolderAdded(this, sourceFolder);
        update();
      }
    }

    return null;
  }

  @Nullable
  protected SourceFolder doAddSourceFolder(@NotNull final VirtualFile file, final boolean isTestSource) {
    final ContentEntry contentEntry = getContentEntry();
    return contentEntry != null ? contentEntry.addSourceFolder(file, isTestSource) : null;
  }

  public void removeSourceFolder(@NotNull final SourceFolder sourceFolder) {
    try {
      doRemoveSourceFolder(sourceFolder);
    }
    finally {
      myEventDispatcher.getMulticaster().sourceFolderRemoved(this, sourceFolder.getFile(), sourceFolder.isTestSource());
      update();
    }
  }

  protected void doRemoveSourceFolder(@NotNull final SourceFolder sourceFolder) {
    final ContentEntry contentEntry = getContentEntry();
    if (contentEntry != null) contentEntry.removeSourceFolder(sourceFolder);
  }

  @Nullable
  public ExcludeFolder addExcludeFolder(@NotNull final VirtualFile file) {
    try {
      return doAddExcludeFolder(file);
    }
    finally {
      myEventDispatcher.getMulticaster().folderExcluded(this, file);
      update();
    }
  }

  @Nullable
  protected ExcludeFolder doAddExcludeFolder(@NotNull final VirtualFile file) {
    final ContentEntry contentEntry = getContentEntry();
    return contentEntry != null ? contentEntry.addExcludeFolder(file) : null;
  }

  public void removeExcludeFolder(@NotNull final ExcludeFolder excludeFolder) {
    try {
      doRemoveExcludeFolder(excludeFolder);
    }
    finally {
      myEventDispatcher.getMulticaster().folderIncluded(this, excludeFolder.getFile());
      update();
    }
  }

  protected void doRemoveExcludeFolder(@NotNull final ExcludeFolder excludeFolder) {
    if (!excludeFolder.isSynthetic()) {
      final ContentEntry contentEntry = getContentEntry();
      if (contentEntry != null) contentEntry.removeExcludeFolder(excludeFolder);
    }
  }

  public boolean isSource(@NotNull final VirtualFile file) {
    final SourceFolder sourceFolder = getSourceFolder(file);
    return sourceFolder != null && !sourceFolder.isTestSource();
  }

  public boolean isTestSource(@NotNull final VirtualFile file) {
    final SourceFolder sourceFolder = getSourceFolder(file);
    return sourceFolder != null && sourceFolder.isTestSource();
  }

  public boolean isExcluded(@NotNull final VirtualFile file) {
    return getExcludeFolder(file) != null;
  }

  public boolean isUnderExcludedDirectory(@NotNull final VirtualFile file) {
    final ContentEntry contentEntry = getContentEntry();
    if (contentEntry == null) {
      return false;
    }
    final ExcludeFolder[] excludeFolders = contentEntry.getExcludeFolders();
    for (ExcludeFolder excludeFolder : excludeFolders) {
      final VirtualFile excludedDir = excludeFolder.getFile();
      if (excludedDir == null) {
        continue;
      }
      if (VfsUtilCore.isAncestor(excludedDir, file, true)) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  public ExcludeFolder getExcludeFolder(@NotNull final VirtualFile file) {
    final ContentEntry contentEntry = getContentEntry();
    if (contentEntry == null) {
      return null;
    }
    final ExcludeFolder[] excludeFolders = contentEntry.getExcludeFolders();
    for (final ExcludeFolder excludeFolder : excludeFolders) {
      final VirtualFile f = excludeFolder.getFile();
      if (f == null) {
        continue;
      }
      if (f.equals(file)) {
        return excludeFolder;
      }
    }
    return null;
  }

  @Nullable
  public SourceFolder getSourceFolder(@NotNull final VirtualFile file) {
    final ContentEntry contentEntry = getContentEntry();
    if (contentEntry == null) {
      return null;
    }
    final SourceFolder[] sourceFolders = contentEntry.getSourceFolders();
    for (SourceFolder sourceFolder : sourceFolders) {
      final VirtualFile f = sourceFolder.getFile();
      if (f != null && f.equals(file)) {
        return sourceFolder;
      }
    }
    return null;
  }
}

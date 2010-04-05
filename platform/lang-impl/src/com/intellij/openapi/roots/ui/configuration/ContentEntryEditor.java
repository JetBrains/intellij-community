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

package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.EventListener;

/**
 * @author Eugene Zhuravlev
 * Date: Oct 8, 2003
 * Time: 3:48:13 PM
 */
public abstract class ContentEntryEditor implements ContentRootPanel.ActionCallback {

  private boolean myIsSelected;
  private ContentRootPanel myContentRootPanel;
  private JPanel myMainPanel;
  protected EventDispatcher<ContentEntryEditorListener> myEventDispatcher;
  private final String myContentEntryUrl;
  protected final boolean myCanMarkSources;
  protected final boolean myCanMarkTestSources;

  public interface ContentEntryEditorListener extends EventListener{
    void editingStarted(ContentEntryEditor editor);
    void beforeEntryDeleted(ContentEntryEditor editor);
    void sourceFolderAdded(ContentEntryEditor editor, SourceFolder folder);
    void sourceFolderRemoved(ContentEntryEditor editor, VirtualFile file, boolean isTestSource);
    void folderExcluded(ContentEntryEditor editor, VirtualFile file);
    void folderIncluded(ContentEntryEditor editor, VirtualFile file);
    void navigationRequested(ContentEntryEditor editor, VirtualFile file);
    void packagePrefixSet(ContentEntryEditor editor, SourceFolder folder);
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
      public void mouseClicked(MouseEvent e) {
        myEventDispatcher.getMulticaster().editingStarted(ContentEntryEditor.this);
      }
      public void mouseEntered(MouseEvent e) {
        if (!myIsSelected) {
          highlight(true);
        }
      }
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
    final ContentEntry[] entries = getModel().getContentEntries();
    for (ContentEntry entry : entries) {
      if (entry.getUrl().equals(myContentEntryUrl)) return entry;
    }

    return null;
  }

  protected abstract ModifiableRootModel getModel();

  public void deleteContentEntry() {
    final int answer = Messages.showYesNoDialog(ProjectBundle.message("module.paths.remove.content.prompt",
                                                                      VirtualFileManager.extractPath(myContentEntryUrl).replace('/', File.separatorChar)),
                                                ProjectBundle.message("module.paths.remove.content.title"), Messages.getQuestionIcon());
    if (answer != 0) { // no
      return;
    }
    myEventDispatcher.getMulticaster().beforeEntryDeleted(this);
    getModel().removeContentEntry(getContentEntry());
  }

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

  public void navigateFolder(ContentEntry contentEntry, ContentFolder contentFolder) {
    final VirtualFile file = contentFolder.getFile();
    if (file != null) { // file can be deleted externally
      myEventDispatcher.getMulticaster().navigationRequested(this, file);
    }
  }

  public void setPackagePrefix(SourceFolder folder, String prefix) {
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
  public SourceFolder addSourceFolder(VirtualFile file, boolean isTestSource) {
    final ContentEntry contentEntry = getContentEntry();
    if (contentEntry != null) {
      final SourceFolder sourceFolder = contentEntry.addSourceFolder(file, isTestSource);
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

  public void removeSourceFolder(SourceFolder sourceFolder) {
    final VirtualFile file = sourceFolder.getFile();
    final boolean isTestSource = sourceFolder.isTestSource();
    try {
      doRemoveSourceFolder(sourceFolder);
    }
    finally {
      myEventDispatcher.getMulticaster().sourceFolderRemoved(this, file, isTestSource);
      update();
    }
  }

  protected void doRemoveSourceFolder(SourceFolder sourceFolder) {
    final ContentEntry contentEntry = getContentEntry();
    if (contentEntry != null) contentEntry.removeSourceFolder(sourceFolder);
  }

  @Nullable
  public ExcludeFolder addExcludeFolder(VirtualFile file) {
    try {
      return doAddExcludeFolder(file);
    }
    finally {
      myEventDispatcher.getMulticaster().folderExcluded(this, file);
      update();
    }
  }

  @Nullable
  protected ExcludeFolder doAddExcludeFolder(VirtualFile file) {
    final ContentEntry contentEntry = getContentEntry();
    if (contentEntry != null) {
      return contentEntry.addExcludeFolder(file);
    }

    return null;
  }

  public void removeExcludeFolder(ExcludeFolder excludeFolder) {
    final VirtualFile file = excludeFolder.getFile();
    try {
      doRemoveExcludeFolder(excludeFolder, file);
    }
    finally {
      myEventDispatcher.getMulticaster().folderIncluded(this, file);
      update();
    }
  }

  protected void doRemoveExcludeFolder(ExcludeFolder excludeFolder, VirtualFile file) {
    if (!excludeFolder.isSynthetic()) {
      final ContentEntry contentEntry = getContentEntry();
      if (contentEntry != null) contentEntry.removeExcludeFolder(excludeFolder);
    }
  }

  public boolean isSource(VirtualFile file) {
    final SourceFolder sourceFolder = getSourceFolder(file);
    return sourceFolder != null && !sourceFolder.isTestSource();
  }

  public boolean isTestSource(VirtualFile file) {
    final SourceFolder sourceFolder = getSourceFolder(file);
    return sourceFolder != null && sourceFolder.isTestSource();
  }

  public boolean isExcluded(VirtualFile file) {
    return getExcludeFolder(file) != null;
  }

  public boolean isUnderExcludedDirectory(final VirtualFile file) {
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
      if (VfsUtil.isAncestor(excludedDir, file, true)) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  public ExcludeFolder getExcludeFolder(VirtualFile file) {
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
  public SourceFolder getSourceFolder(VirtualFile file) {
    final ContentEntry contentEntry = getContentEntry();
    if (contentEntry == null) {
      return null;
    }
    final SourceFolder[] sourceFolders = contentEntry.getSourceFolders();
    for (SourceFolder sourceFolder : sourceFolders) {
      final VirtualFile f = sourceFolder.getFile();
      if (f == null) {
        continue;
      }
      if (f.equals(file)) {
        return sourceFolder;
      }
    }
    return null;
  }
}

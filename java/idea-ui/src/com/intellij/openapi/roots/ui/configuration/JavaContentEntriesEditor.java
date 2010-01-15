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

import com.intellij.Patches;
import com.intellij.ide.util.JavaUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.progress.util.SmoothProgressAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.SwingWorker;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JavaContentEntriesEditor extends CommonContentEntriesEditor {
  public JavaContentEntriesEditor(String moduleName, ModuleConfigurationState state) {
    super(moduleName, state, true, true);
  }

  protected ContentEntryEditor createContentEntryEditor(final String contentEntryUrl) {
    return new JavaContentEntryEditor(contentEntryUrl) {
      @Override
      protected ModifiableRootModel getModel() {
        return JavaContentEntriesEditor.this.getModel();
      }
    };
  }

  protected ContentEntryTreeEditor createContentEntryTreeEditor(Project project) {
    return new ContentEntryTreeEditor(project, true, true);
  }

  @Override
  protected List<ContentEntry> addContentEntries(VirtualFile[] files) {
    List<ContentEntry> contentEntries = super.addContentEntries(files);
    if (!contentEntries.isEmpty()) {
      final ContentEntry[] contentEntriesArray = contentEntries.toArray(new ContentEntry[contentEntries.size()]);
      addSourceRoots(myProject, contentEntriesArray, new Runnable() {
        public void run() {
          addContentEntryPanels(contentEntriesArray);
        }
      });
    }
    return contentEntries;
  }

  private static void addSourceRoots(final Project project, final ContentEntry[] contentEntries, final Runnable finishRunnable) {
    final HashMap<ContentEntry, List<Pair<File, String>>> entryToRootMap = new HashMap<ContentEntry, List<Pair<File, String>>>();
    final Map<File, ContentEntry> fileToEntryMap = new HashMap<File, ContentEntry>();
    for (final ContentEntry contentEntry : contentEntries) {
      final VirtualFile file = contentEntry.getFile();
      if (file != null) {
        entryToRootMap.put(contentEntry, null);
        fileToEntryMap.put(VfsUtil.virtualToIoFile(file), contentEntry);
      }
    }

    final ProgressWindow progressWindow = new ProgressWindow(true, project);
    final ProgressIndicator progressIndicator = Patches.MAC_HIDE_QUIT_HACK
                                                ? progressWindow
                                                : new SmoothProgressAdapter(progressWindow, project);

    final Runnable searchRunnable = new Runnable() {
      public void run() {
        final Runnable process = new Runnable() {
          public void run() {
            for (final File file : fileToEntryMap.keySet()) {
              progressIndicator.setText(ProjectBundle.message("module.paths.searching.source.roots.progress", file.getPath()));
              final List<Pair<File, String>> roots = JavaUtil.suggestRoots(file);
              entryToRootMap.put(fileToEntryMap.get(file), roots);
            }
          }
        };
        progressWindow.setTitle(ProjectBundle.message("module.paths.searching.source.roots.title"));
        ProgressManager.getInstance().runProcess(process, progressIndicator);
      }
    };

    final Runnable addSourcesRunnable = new Runnable() {
      public void run() {
        for (final ContentEntry contentEntry : contentEntries) {
          final List<Pair<File, String>> suggestedRoots = entryToRootMap.get(contentEntry);
          if (suggestedRoots != null) {
            for (final Pair<File, String> suggestedRoot : suggestedRoots) {
              final VirtualFile sourceRoot = LocalFileSystem.getInstance().findFileByIoFile(suggestedRoot.first);
              final VirtualFile fileContent = contentEntry.getFile();
              if (sourceRoot != null && fileContent != null && VfsUtil.isAncestor(fileContent, sourceRoot, false)) {
                contentEntry.addSourceFolder(sourceRoot, false, suggestedRoot.getSecond());
              }
            }
          }
        }
        if (finishRunnable != null) {
          finishRunnable.run();
        }
      }
    };

    new SwingWorker() {
      public Object construct() {
        searchRunnable.run();
        return null;
      }

      public void finished() {
        addSourcesRunnable.run();
      }
    }.start();
  }

  protected JPanel createBottomControl(Module module) {
    final JPanel innerPanel = new JPanel(new GridBagLayout());
    innerPanel.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 6));
    return innerPanel;
  }
}

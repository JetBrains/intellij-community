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
import com.intellij.openapi.roots.ContentFolder;
import com.intellij.openapi.roots.ExcludeFolder;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.roots.IconActionComponent;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public abstract class JavaContentRootPanel extends ContentRootPanel {
  private static final Color SOURCES_COLOR = new Color(0x0A50A1);
  private static final Icon ADD_PREFIX_ICON = IconLoader.getIcon("/modules/setPackagePrefix.png");
  private static final Icon ADD_PREFIX_ROLLOVER_ICON = IconLoader.getIcon("/modules/setPackagePrefixRollover.png");

  public JavaContentRootPanel(ActionCallback callback) {
    super(callback);
  }

  @Nullable
  protected JComponent createAdditionalComponent(ContentFolder folder) {
    if (folder instanceof SourceFolder) {
      return createAddPrefixComponent((SourceFolder)folder);
    }
    return null;
  }

  private JComponent createAddPrefixComponent(final SourceFolder folder) {
    final IconActionComponent iconComponent = new IconActionComponent(ADD_PREFIX_ICON, ADD_PREFIX_ROLLOVER_ICON,
                                                                      ProjectBundle.message("module.paths.package.prefix.tooltip"), new Runnable() {
      public void run() {
        final String message = ProjectBundle.message("module.paths.package.prefix.prompt",
                                                     toRelativeDisplayPath(folder.getUrl(), getContentEntry().getUrl() + ":"));
        final String prefix = Messages.showInputDialog(JavaContentRootPanel.this, message,
                                                       ProjectBundle.message("module.paths.package.prefix.title"), Messages.getQuestionIcon(), folder.getPackagePrefix(), null);
        if (prefix != null) {
          myCallback.setPackagePrefix(folder, prefix);
        }
      }
    });
    final JPanel panel = new JPanel(new BorderLayout());
    panel.setOpaque(false);
    panel.add(iconComponent, BorderLayout.CENTER);
    panel.add(Box.createHorizontalStrut(3), BorderLayout.EAST);
    return panel;
  }

  protected void addFolderGroupComponents() {
    final List<ContentFolder> sources = new ArrayList<ContentFolder>();
    final List<ContentFolder> testSources = new ArrayList<ContentFolder>();
    final List<ContentFolder> excluded = new ArrayList<ContentFolder>();
    final SourceFolder[] sourceFolders = getContentEntry().getSourceFolders();
    for (SourceFolder folder : sourceFolders) {
      if (folder.isSynthetic()) {
        continue;
      }
      final VirtualFile folderFile = folder.getFile();
      if (folderFile != null && (isExcluded(folderFile) || isUnderExcludedDirectory(folderFile))) {
        continue;
      }
      if (folder.isTestSource()) {
        testSources.add(folder);
      }
      else {
        sources.add(folder);
      }
    }

    final ExcludeFolder[] excludeFolders = getContentEntry().getExcludeFolders();
    for (final ExcludeFolder excludeFolder : excludeFolders) {
      if (!excludeFolder.isSynthetic()) {
        excluded.add(excludeFolder);
      }
    }

    if (sources.size() > 0) {
      final JComponent sourcesComponent = createFolderGroupComponent(ProjectBundle.message("module.paths.sources.group"), sources.toArray(new ContentFolder[sources.size()]), SOURCES_COLOR);
      this.add(sourcesComponent, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTH, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 10, 0), 0, 0));
    }
    if (testSources.size() > 0) {
      final JComponent testSourcesComponent = createFolderGroupComponent(ProjectBundle.message("module.paths.test.sources.group"), testSources.toArray(new ContentFolder[testSources.size()]), TESTS_COLOR);
      this.add(testSourcesComponent, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTH, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 10, 0), 0, 0));
    }
    if (excluded.size() > 0) {
      final JComponent excludedComponent = createFolderGroupComponent(ProjectBundle.message("module.paths.excluded.group"), excluded.toArray(new ContentFolder[excluded.size()]), EXCLUDED_COLOR);
      this.add(excludedComponent, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTH, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 10, 0), 0, 0));
    }
  }
}

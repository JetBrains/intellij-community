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
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.roots.IconActionComponent;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public abstract class JavaContentRootPanel extends ContentRootPanel {
  private static final Icon ADD_PREFIX_ICON = IconLoader.getIcon("/modules/setPackagePrefix.png");
  private static final Icon ADD_PREFIX_ROLLOVER_ICON = IconLoader.getIcon("/modules/setPackagePrefixRollover.png");

  public JavaContentRootPanel(ActionCallback callback) {
    super(callback, true, true);
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
}

/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.roots.IconActionComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsSimpleElement;
import org.jetbrains.jps.model.java.JavaSourceRootProperties;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import javax.swing.*;
import java.awt.*;

/**
 * @author nik
 */
public abstract class JavaSourceRootEditHandlerBase extends ModuleSourceRootEditHandler<JpsSimpleElement<JavaSourceRootProperties>> {
  public JavaSourceRootEditHandlerBase(JpsModuleSourceRootType<JpsSimpleElement<JavaSourceRootProperties>> rootType) {
    super(rootType);
  }

  @Nullable
  @Override
  public String getPropertiesString(@NotNull JpsSimpleElement<JavaSourceRootProperties> properties) {
    String packagePrefix = properties.getData().getPackagePrefix();
    return packagePrefix.isEmpty() ? null : " (" + packagePrefix + ")";
  }

  @Nullable
  @Override
  public JComponent createPropertiesEditor(@NotNull final SourceFolder folder,
                                           @NotNull final JComponent parentComponent,
                                           @NotNull final ContentRootPanel.ActionCallback callback) {
    final IconActionComponent iconComponent = new IconActionComponent(AllIcons.Modules.SetPackagePrefix,
                                                                      AllIcons.Modules.SetPackagePrefixRollover,
                                                                      ProjectBundle.message("module.paths.package.prefix.tooltip"), new Runnable() {
      @Override
      public void run() {
        final String message = ProjectBundle.message("module.paths.package.prefix.prompt",
                                                     ContentRootPanel.toRelativeDisplayPath(folder.getUrl(), folder.getContentEntry().getUrl() + ":"));
        final String prefix = Messages.showInputDialog(parentComponent, message,
                                                       ProjectBundle.message("module.paths.package.prefix.title"),
                                                       Messages.getQuestionIcon(), folder.getPackagePrefix(), null);
        if (prefix != null) {
          folder.setPackagePrefix(prefix);
          callback.onSourceRootPropertiesChanged(folder);
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

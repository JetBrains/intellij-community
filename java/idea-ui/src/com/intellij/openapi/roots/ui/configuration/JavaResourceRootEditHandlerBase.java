// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.icons.AllIcons;
import com.intellij.ide.JavaUiBundle;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.roots.IconActionComponent;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;
import org.jetbrains.jps.model.java.JavaResourceRootProperties;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import javax.swing.*;
import java.awt.*;

public abstract class JavaResourceRootEditHandlerBase extends ModuleSourceRootEditHandler<JavaResourceRootProperties> {
  public JavaResourceRootEditHandlerBase(JpsModuleSourceRootType<JavaResourceRootProperties> rootType) {
    super(rootType);
  }

  @Nullable
  @Override
  public Icon getFolderUnderRootIcon() {
    return null;
  }

  @Nullable
  @Override
  public CustomShortcutSet getMarkRootShortcutSet() {
    return null;
  }

  @NotNull
  @Override
  public Icon getRootIcon(@NotNull JavaResourceRootProperties properties) {
    return properties.isForGeneratedSources() ? getGeneratedRootIcon() : getRootIcon();
  }

  @NotNull
  protected Icon getGeneratedRootIcon() {
    return getRootIcon();
  }

  @Nullable
  @Override
  public String getPropertiesString(@NotNull JavaResourceRootProperties properties) {
    StringBuilder buffer = new StringBuilder();
    if (properties.isForGeneratedSources()) {
      buffer.append(" [generated]");
    }
    String relativeOutputPath = properties.getRelativeOutputPath();
    if (!relativeOutputPath.isEmpty()) {
      buffer.append(" (").append(relativeOutputPath).append(")");
    }
    return buffer.length() > 0 ? buffer.toString() : null;
  }

  @Nullable
  @Override
  public JComponent createPropertiesEditor(@NotNull final SourceFolder folder,
                                           @NotNull final JComponent parentComponent,
                                           @NotNull final ContentRootPanel.ActionCallback callback) {
    final IconActionComponent iconComponent = new IconActionComponent(AllIcons.General.Inline_edit,
                                                                      AllIcons.General.Inline_edit_hovered,
                                                                      ProjectBundle.message("module.paths.edit.properties.tooltip"),
                                                                      () -> {
                                                                        JavaResourceRootProperties properties = folder.getJpsElement().getProperties( JavaModuleSourceRootTypes.RESOURCES);
                                                                        assert properties != null;
                                                                        ResourceRootPropertiesDialog
                                                                          dialog = new ResourceRootPropertiesDialog(parentComponent, properties);
                                                                        if (dialog.showAndGet()) {
                                                                          callback.onSourceRootPropertiesChanged(folder);
                                                                        }
                                                                      });
    final JPanel panel = new JPanel(new BorderLayout());
    panel.setOpaque(false);
    panel.add(iconComponent, BorderLayout.CENTER);
    panel.add(Box.createHorizontalStrut(3), BorderLayout.EAST);
    return panel;
  }

  private static final class ResourceRootPropertiesDialog extends DialogWrapper {
    private final JTextField myRelativeOutputPathField;
    private final JCheckBox myIsGeneratedCheckBox;
    private final JPanel myMainPanel;
    @NotNull private final JavaResourceRootProperties myProperties;

    private ResourceRootPropertiesDialog(@NotNull JComponent parentComponent, @NotNull JavaResourceRootProperties properties) {
      super(parentComponent, true);
      myProperties = properties;
      setTitle(ProjectBundle.message("module.paths.edit.properties.title"));
      myRelativeOutputPathField = new JTextField();
      myIsGeneratedCheckBox = new JCheckBox(JavaUiBundle.message("checkbox.for.generated.resources"));
      myMainPanel = FormBuilder.createFormBuilder()
        .addLabeledComponent(JavaUiBundle.message("label.relative.output.path"), myRelativeOutputPathField)
        .addComponent(myIsGeneratedCheckBox)
        .getPanel();
      myRelativeOutputPathField.setText(myProperties.getRelativeOutputPath());
      myRelativeOutputPathField.setColumns(25);
      myIsGeneratedCheckBox.setSelected(myProperties.isForGeneratedSources());
      init();
    }

    @Nullable
    @Override
    public JComponent getPreferredFocusedComponent() {
      return myRelativeOutputPathField;
    }

    @Override
    protected void doOKAction() {
      myProperties.setRelativeOutputPath(normalizePath(myRelativeOutputPathField.getText()));
      myProperties.setForGeneratedSources(myIsGeneratedCheckBox.isSelected());
      super.doOKAction();
    }

    @NotNull
    private static String normalizePath(String path) {
      return StringUtil.trimEnd(StringUtil.trimStart(FileUtil.toSystemIndependentName(path.trim()), "/"), "/");
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
      return myMainPanel;
    }
  }
}

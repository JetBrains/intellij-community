// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

  @Override
  public @Nullable Icon getFolderUnderRootIcon() {
    return null;
  }

  @Override
  public @Nullable CustomShortcutSet getMarkRootShortcutSet() {
    return null;
  }

  @Override
  public @NotNull Icon getRootIcon(@NotNull JavaResourceRootProperties properties) {
    return properties.isForGeneratedSources() ? getGeneratedRootIcon() : getRootIcon();
  }

  protected @NotNull Icon getGeneratedRootIcon() {
    return getRootIcon();
  }

  @Override
  public @Nullable String getPropertiesString(@NotNull JavaResourceRootProperties properties) {
    StringBuilder buffer = new StringBuilder();
    if (properties.isForGeneratedSources()) {
      buffer.append(" [generated]");
    }
    String relativeOutputPath = properties.getRelativeOutputPath();
    if (!relativeOutputPath.isEmpty()) {
      buffer.append(" (").append(relativeOutputPath).append(")");
    }
    return !buffer.isEmpty() ? buffer.toString() : null;
  }

  @Override
  public @Nullable JComponent createPropertiesEditor(final @NotNull SourceFolder folder,
                                                     final @NotNull JComponent parentComponent,
                                                     final @NotNull ContentRootPanel.ActionCallback callback) {
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
    private final @NotNull JavaResourceRootProperties myProperties;

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

    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
      return myRelativeOutputPathField;
    }

    @Override
    protected void doOKAction() {
      myProperties.setRelativeOutputPath(normalizePath(myRelativeOutputPathField.getText()));
      myProperties.setForGeneratedSources(myIsGeneratedCheckBox.isSelected());
      super.doOKAction();
    }

    private static @NotNull String normalizePath(String path) {
      return StringUtil.trimEnd(StringUtil.trimStart(FileUtil.toSystemIndependentName(path.trim()), "/"), "/");
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
      return myMainPanel;
    }
  }
}

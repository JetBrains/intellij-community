// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.roots.IconActionComponent;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;
import org.jetbrains.jps.model.java.JavaSourceRootProperties;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import javax.swing.*;
import java.awt.*;

public abstract class JavaSourceRootEditHandlerBase extends ModuleSourceRootEditHandler<JavaSourceRootProperties> {
  public JavaSourceRootEditHandlerBase(JpsModuleSourceRootType<JavaSourceRootProperties> rootType) {
    super(rootType);
  }

  @Override
  public @NotNull Icon getRootIcon(@NotNull JavaSourceRootProperties properties) {
    return properties.isForGeneratedSources() ? getGeneratedRootIcon() : getRootIcon();
  }

  @Override
  public @Nullable Icon getRootFileLayerIcon() {
    return AllIcons.Modules.SourceRootFileLayer;
  }

  protected abstract @NotNull Icon getGeneratedRootIcon();

  @Override
  public @Nullable String getPropertiesString(@NotNull JavaSourceRootProperties properties) {
    StringBuilder buffer = new StringBuilder();
    if (properties.isForGeneratedSources()) {
      buffer.append(" [generated]");
    }
    String packagePrefix = properties.getPackagePrefix();
    if (!packagePrefix.isEmpty()) {
      buffer.append(" (").append(packagePrefix).append(")");
    }
    return buffer.length() > 0 ? buffer.toString() : null;
  }

  @Override
  public @Nullable JComponent createPropertiesEditor(final @NotNull SourceFolder folder,
                                                     final @NotNull JComponent parentComponent,
                                                     final @NotNull ContentRootPanel.ActionCallback callback) {
    final IconActionComponent iconComponent = new IconActionComponent(AllIcons.General.Inline_edit,
                                                                      AllIcons.General.Inline_edit_hovered,
                                                                      ProjectBundle.message("module.paths.edit.properties.tooltip"), () -> {
                                                                        JavaSourceRootProperties properties = folder.getJpsElement().getProperties(JavaModuleSourceRootTypes.SOURCES);
                                                                        assert properties != null;
                                                                        SourceRootPropertiesDialog dialog = new SourceRootPropertiesDialog(parentComponent, properties);
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

  private static final class SourceRootPropertiesDialog extends DialogWrapper {
    private final JTextField myPackagePrefixField;
    private final JCheckBox myIsGeneratedCheckBox;
    private final JPanel myMainPanel;
    private final @NotNull JavaSourceRootProperties myProperties;

    private SourceRootPropertiesDialog(@NotNull JComponent parentComponent, @NotNull JavaSourceRootProperties properties) {
      super(parentComponent, true);
      myProperties = properties;
      setTitle(ProjectBundle.message("module.paths.edit.properties.title"));
      myPackagePrefixField = new JTextField();
      myIsGeneratedCheckBox = new JCheckBox(ProjectBundle.message("checkbox.for.generated.sources"));
      myMainPanel = FormBuilder.createFormBuilder()
        .addLabeledComponent(ProjectBundle.message("label.package.prefix"), myPackagePrefixField)
        .addComponent(myIsGeneratedCheckBox)
        .getPanel();
      myPackagePrefixField.setText(myProperties.getPackagePrefix());
      myPackagePrefixField.setColumns(25);
      myIsGeneratedCheckBox.setSelected(myProperties.isForGeneratedSources());
      init();
    }

    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
      return myPackagePrefixField;
    }

    @Override
    protected void doOKAction() {
      myProperties.setPackagePrefix(myPackagePrefixField.getText().trim());
      myProperties.setForGeneratedSources(myIsGeneratedCheckBox.isSelected());
      super.doOKAction();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
      return myMainPanel;
    }
  }
}

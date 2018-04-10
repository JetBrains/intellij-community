// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema;

import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class JsonSchemaCatalogConfigurable extends BaseConfigurable {
  @NonNls public static final String SETTINGS_JSON_SCHEMA_CATALOG = "settings.json.schema.catalog";
  public static final String JSON_SCHEMA_CATALOG = "JSON Schema Catalog";
  @NotNull private final Project myProject;
  private final JBCheckBox myCheckBox;

  public JsonSchemaCatalogConfigurable(@NotNull final Project project) {
    myProject = project;
    myCheckBox = new JBCheckBox("Automatically fetch JSON schemas defined by schemastore.org catalog");
    myCheckBox.setToolTipText("Automatically assign and download JSON schemas based on file mappings provided at http://schemastore.org/json/");
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    FormBuilder builder = FormBuilder.createFormBuilder();
    builder.addComponent(new TitledSeparator("Default schema catalog"));
    builder.addComponent(myCheckBox);
    return wrap(builder.getPanel());
  }

  private static JPanel wrap(JComponent panel) {
    JPanel wrapper = new JBPanel(new BorderLayout());
    wrapper.add(panel, BorderLayout.NORTH);

    return wrapper;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myCheckBox;
  }

  @Override
  public void reset() {
    JsonSchemaCatalogProjectConfiguration.MyState state = JsonSchemaCatalogProjectConfiguration.getInstance(myProject).getState();
    myCheckBox.setSelected(state == null || state.myIsEnabled);
  }

  @Override
  public boolean isModified() {
    JsonSchemaCatalogProjectConfiguration.MyState state = JsonSchemaCatalogProjectConfiguration.getInstance(myProject).getState();
    return state == null || state.myIsEnabled != myCheckBox.isSelected();
  }

  @Override
  public void apply() throws ConfigurationException {
    JsonSchemaCatalogProjectConfiguration.getInstance(myProject).setState(myCheckBox.isSelected());
  }

  @Nls(capitalization = Nls.Capitalization.Title)
  @Override
  public String getDisplayName() {
    return JSON_SCHEMA_CATALOG;
  }

  @Nullable
  @Override
  public String getHelpTopic() {
    return SETTINGS_JSON_SCHEMA_CATALOG;
  }
}

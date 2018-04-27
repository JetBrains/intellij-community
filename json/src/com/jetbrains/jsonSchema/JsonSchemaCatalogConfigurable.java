// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.panel.ComponentPanelBuilder;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class JsonSchemaCatalogConfigurable implements Configurable {
  @NonNls public static final String SETTINGS_JSON_SCHEMA_CATALOG = "settings.json.schema.catalog";
  public static final String JSON_SCHEMA_CATALOG = "JSON Schema Catalog";
  @NotNull private final Project myProject;
  private final JBCheckBox myCheckBox;

  public JsonSchemaCatalogConfigurable(@NotNull final Project project) {
    myProject = project;
    myCheckBox = new JBCheckBox("Use schemastore.org JSON Schema catalog");
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    FormBuilder builder = FormBuilder.createFormBuilder();
    JLabel commentComponent =
      ComponentPanelBuilder.createCommentComponent("Schemas will be downloaded and assigned using the <a href=\"http://schemastore.org/json/\">SchemaStore API</a>", true);
    builder.addComponent(myCheckBox);
    commentComponent.setBorder(JBUI.Borders.emptyLeft(18));
    builder.addComponent(commentComponent);
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

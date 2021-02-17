// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema;

import com.intellij.json.JsonBundle;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.panel.ComponentPanelBuilder;
import com.intellij.openapi.util.NlsContexts.DetailedDescription;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class JsonSchemaCatalogConfigurable implements Configurable {
  @NonNls public static final String SETTINGS_JSON_SCHEMA_CATALOG = "settings.json.schema.catalog";
  @NotNull private final Project myProject;
  private final JBCheckBox myCatalogCheckBox;
  private final JBCheckBox myRemoteCheckBox;
  private final JBCheckBox myPreferRemoteCheckBox;

  public JsonSchemaCatalogConfigurable(@NotNull final Project project) {
    myProject = project;
    myCatalogCheckBox = new JBCheckBox(JsonBundle.message("checkbox.use.schemastore.org.json.schema.catalog"));
    myRemoteCheckBox = new JBCheckBox(JsonBundle.message("checkbox.allow.downloading.json.schemas.from.remote.sources"));
    myPreferRemoteCheckBox = new JBCheckBox(JsonBundle.message("checkbox.always.download.the.most.recent.version.of.schemas"));
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    FormBuilder builder = FormBuilder.createFormBuilder();

    builder.addComponent(myRemoteCheckBox);
    builder.addVerticalGap(2);
    myRemoteCheckBox.addChangeListener(c -> {
      boolean selected = myRemoteCheckBox.isSelected();
      myCatalogCheckBox.setEnabled(selected);
      myPreferRemoteCheckBox.setEnabled(selected);
      if (!selected) {
        myCatalogCheckBox.setSelected(false);
        myPreferRemoteCheckBox.setSelected(false);
      }
    });
    addWithComment(builder, myCatalogCheckBox, JsonBundle.message("schema.catalog.hint"));
    addWithComment(builder, myPreferRemoteCheckBox, JsonBundle.message("schema.catalog.remote.hint"));
    return wrap(builder.getPanel());
  }

  private static void addWithComment(FormBuilder builder, JBCheckBox box, @DetailedDescription String s) {
    builder.addComponent(new ComponentPanelBuilder(box).withComment(s).createPanel());
  }

  private static JPanel wrap(JComponent panel) {
    JPanel wrapper = new JBPanel(new BorderLayout());
    wrapper.add(panel, BorderLayout.NORTH);
    return wrapper;
  }

  @Override
  public @Nullable JComponent getPreferredFocusedComponent() {
    return myCatalogCheckBox;
  }

  @Override
  public void reset() {
    JsonSchemaCatalogProjectConfiguration.MyState state = JsonSchemaCatalogProjectConfiguration.getInstance(myProject).getState();
    final boolean remoteEnabled = state == null || state.myIsRemoteActivityEnabled;
    myRemoteCheckBox.setSelected(remoteEnabled);
    myCatalogCheckBox.setEnabled(remoteEnabled);
    myPreferRemoteCheckBox.setEnabled(remoteEnabled);
    myCatalogCheckBox.setSelected(state == null || state.myIsCatalogEnabled);
    myPreferRemoteCheckBox.setSelected(state == null || state.myIsPreferRemoteSchemas);
  }

  @Override
  public boolean isModified() {
    JsonSchemaCatalogProjectConfiguration.MyState state = JsonSchemaCatalogProjectConfiguration.getInstance(myProject).getState();
    return state == null
           || state.myIsCatalogEnabled != myCatalogCheckBox.isSelected()
           || state.myIsPreferRemoteSchemas != myPreferRemoteCheckBox.isSelected()
           || state.myIsRemoteActivityEnabled != myRemoteCheckBox.isSelected();
  }

  @Override
  public void apply() throws ConfigurationException {
    JsonSchemaCatalogProjectConfiguration.getInstance(myProject).setState(myCatalogCheckBox.isSelected(),
                                                                          myRemoteCheckBox.isSelected(),
                                                                          myPreferRemoteCheckBox.isSelected());
  }

  @Nls(capitalization = Nls.Capitalization.Title)
  @Override
  public String getDisplayName() {
    return JsonBundle.message("configurable.JsonSchemaCatalogConfigurable.display.name");
  }

  @Nullable
  @Override
  public String getHelpTopic() {
    return SETTINGS_JSON_SCHEMA_CATALOG;
  }
}

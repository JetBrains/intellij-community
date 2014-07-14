/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.Alarm;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

/**
 * @author Konstantin Bulenkov
 */
public class ErrorPaneConfigurable extends JPanel implements Configurable, Disposable, ConfigurationErrors {
  private final Project myProject;
  private final StructureConfigurableContext myContext;
  private final Alarm myAlarm;
  private final ArrayList<ConfigurationError> myErrors = new ArrayList<ConfigurationError>();
  private final JTextPane myContent = new JTextPane();

  public ErrorPaneConfigurable(Project project, StructureConfigurableContext context) {
    super(new BorderLayout());
    myContent.setEditorKit(UIUtil.getHTMLEditorKit());
    myContent.setEditable(false);
    final JScrollPane pane = ScrollPaneFactory.createScrollPane(myContent, true);
    pane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    add(pane);
    myProject = project;
    myContext = context;
    myAlarm = new Alarm(this);
    project.getMessageBus().connect(this).subscribe(ConfigurationErrors.TOPIC, this);

    refresh();
  }

  public void refresh() {
    myAlarm.cancelAllRequests();
    myAlarm.addRequest(new Runnable() {
      @Override
      public void run() {
        String html = "<html><body>";
        int i = 0;
        for (ConfigurationError error : myErrors) {
          i++;
          html+= i + ". " + error.getDescription() + "<br/>";
        }
        html += "</body></html>";
        myContent.setText(html);
      }
    }, 100);
  }

  @Nls
  @Override
  public String getDisplayName() {
    return "Problems";
  }

  @Nullable
  @Override
  public String getHelpTopic() {
    return null;
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    return this;
  }

  @Override
  public boolean isModified() {
    return false;
  }

  @Override
  public void apply() throws ConfigurationException {

  }

  @Override
  public void reset() {

  }

  @Override
  public void disposeUIResources() {
    Disposer.dispose(this);
  }

  @Override
  public void dispose() {
  }

  @Override
  public void addError(@NotNull ConfigurationError error) {
    myErrors.add(error);
    refresh();
  }

  @Override
  public void removeError(@NotNull ConfigurationError error) {
    myErrors.remove(error);
    refresh();
  }
}

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
package com.intellij.openapi.projectRoots.ui;

import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ui.configuration.ProjectJdksConfigurable;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;

import javax.swing.*;
import java.awt.*;

/**
 * @author MYakovlev
 */
public class ProjectJdksEditor extends DialogWrapper {
  private ProjectJdksConfigurable myConfigurable;
  private Sdk myProjectJdk;


  public ProjectJdksEditor(final Sdk jdk, Project project, Component parent) {
    this(jdk, parent, new ProjectJdksConfigurable(project));
  }
  
  public ProjectJdksEditor(final Sdk jdk, Component parent, ProjectJdksConfigurable configurable) {
    super(parent, true);
    myConfigurable = configurable;
    SwingUtilities.invokeLater(new Runnable(){
      @Override
      public void run() {
        myConfigurable.selectNodeInTree(jdk != null ? jdk.getName() : null);
      }
    });
    setTitle(ProjectBundle.message("sdk.configure.title"));
    Disposer.register(myDisposable, new Disposable() {
      @Override
      public void dispose() {
        if (myConfigurable != null) {
          myConfigurable.disposeUIResources();
          myConfigurable = null;
        }
      }
    });
    init();
  }

  public ProjectJdksEditor(Sdk jdk, Component parent){
    this(jdk, CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext()), parent);
  }

  @Override
  protected JComponent createCenterPanel(){
    myConfigurable.reset();
    return myConfigurable.createComponent();
  }

  @Override
  protected void doOKAction(){
    try{
      myProjectJdk = myConfigurable.getSelectedJdk(); //before dispose
      myConfigurable.apply();
      super.doOKAction();
    }
    catch (ConfigurationException e){
      Messages.showMessageDialog(getContentPane(), e.getMessage(),
                                 ProjectBundle.message("sdk.configure.save.settings.error"), Messages.getErrorIcon());
    }
  }

  @Override
  protected String getDimensionServiceKey(){
    return "#com.intellij.openapi.projectRoots.ui.ProjectJdksEditor";
  }

  public Sdk getSelectedJdk(){
    return myProjectJdk;
  }

}
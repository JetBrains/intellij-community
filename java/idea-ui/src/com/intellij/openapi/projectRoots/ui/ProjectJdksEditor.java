// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.ui;

import com.intellij.ide.DataManager;
import com.intellij.ide.JavaUiBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ui.configuration.ProjectJdksConfigurable;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author MYakovlev
 */
public class ProjectJdksEditor extends DialogWrapper {
  private ProjectJdksConfigurable myConfigurable;
  private Sdk myProjectJdk;

  public ProjectJdksEditor(final @Nullable Sdk jdk, @NotNull Project project, @NotNull Component parent) {
    this(jdk, parent, new ProjectJdksConfigurable(project));
  }
  
  public ProjectJdksEditor(final @Nullable Sdk jdk, @NotNull Component parent, @NotNull ProjectJdksConfigurable configurable) {
    super(parent, true);
    myConfigurable = configurable;
    SwingUtilities.invokeLater(() -> myConfigurable.selectNodeInTree(jdk != null ? jdk.getName() : null));
    setTitle(JavaUiBundle.message("sdk.configure.title"));
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
                                 JavaUiBundle.message("sdk.configure.save.settings.error"), Messages.getErrorIcon());
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
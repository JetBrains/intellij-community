// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.ide.JavaUiBundle;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.projectRoots.ui.SdkEditor;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectStructureElement;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.SdkProjectStructureElement;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.ui.navigation.History;
import com.intellij.ui.navigation.Place;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class JdkConfigurable extends ProjectStructureElementConfigurable<Sdk> implements Place.Navigator {
  private final Sdk myProjectJdk;
  private final SdkEditor mySdkEditor;
  private final SdkProjectStructureElement myProjectStructureElement;

  public JdkConfigurable(@NotNull Sdk projectJdk,
                         @NotNull ProjectSdksModel sdksModel,
                         @NotNull Runnable updateTree,
                         @NotNull History history,
                         @NotNull Project project) {
    super(true, updateTree);
    myProjectJdk = projectJdk;
    mySdkEditor = new SdkEditor(project, sdksModel, history, projectJdk);
    final StructureConfigurableContext context = ProjectStructureConfigurable.getInstance(project).getContext();
    myProjectStructureElement = new SdkProjectStructureElement(context, myProjectJdk);
  }

  @Override
  public ProjectStructureElement getProjectStructureElement() {
    return myProjectStructureElement;
  }

  @Override
  public void setDisplayName(final String name) {
    mySdkEditor.setNewSdkName(name);
  }

  @Override
  public Sdk getEditableObject() {
    return myProjectJdk;
  }

  @Override
  public String getBannerSlogan() {
    return JavaUiBundle.message("project.roots.jdk.banner.text", mySdkEditor.getActualSdkName());
  }

  @Override
  public String getDisplayName() {
    return mySdkEditor.getActualSdkName();
  }

  @Override
  public Icon getIcon(boolean open) {
    return ((SdkType) myProjectJdk.getSdkType()).getIcon();
  }

  @Override
  public @Nullable @NonNls String getHelpTopic() {
    return ((SdkType) myProjectJdk.getSdkType()).getHelpTopic();
  }


  @Override
  public JComponent createOptionsPanel() {
    return mySdkEditor.createComponent();
  }

  @Override
  public boolean isModified() {
    return mySdkEditor.isModified();
  }

  @Override
  public void apply() throws ConfigurationException {
    mySdkEditor.apply();
  }

  @Override
  public void reset() {
    mySdkEditor.reset();
  }

  @Override
  public void disposeUIResources() {
    mySdkEditor.disposeUIResources();
  }

  @Override
  public ActionCallback navigateTo(final @Nullable Place place, final boolean requestFocus) {
    return mySdkEditor.navigateTo(place, requestFocus);
  }

  @Override
  public void queryPlace(final @NotNull Place place) {
    mySdkEditor.queryPlace(place);
  }
}

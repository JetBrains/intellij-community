/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl;
import com.intellij.openapi.projectRoots.ui.SdkEditor;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectStructureElement;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.SdkProjectStructureElement;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.ui.navigation.History;
import com.intellij.ui.navigation.Place;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * User: anna
 * Date: 05-Jun-2006
 */
public class JdkConfigurable extends ProjectStructureElementConfigurable<Sdk> implements Place.Navigator {
  private final ProjectJdkImpl myProjectJdk;
  private final SdkEditor mySdkEditor;
  private final SdkProjectStructureElement myProjectStructureElement;

  public JdkConfigurable(final ProjectJdkImpl projectJdk,
                         final ProjectSdksModel sdksModel,
                         final Runnable updateTree, @NotNull History history, Project project) {
    super(true, updateTree);
    myProjectJdk = projectJdk;
    mySdkEditor = createSdkEditor(sdksModel, history, myProjectJdk);
    final StructureConfigurableContext context = ModuleStructureConfigurable.getInstance(project).getContext();
    myProjectStructureElement = new SdkProjectStructureElement(context, myProjectJdk);
  }

  protected SdkEditor createSdkEditor(ProjectSdksModel sdksModel, History history, ProjectJdkImpl projectJdk) {
    return new SdkEditor(sdksModel, history, projectJdk);
  }

  @Override
  public ProjectStructureElement getProjectStructureElement() {
    return myProjectStructureElement;
  }

  @Override
  public void setDisplayName(final String name) {
    myProjectJdk.setName(name);
  }

  @Override
  public Sdk getEditableObject() {
    return myProjectJdk;
  }

  @Override
  public String getBannerSlogan() {
    return ProjectBundle.message("project.roots.jdk.banner.text", myProjectJdk.getName());
  }

  @Override
  public String getDisplayName() {
    return myProjectJdk.getName();
  }

  @Override
  public Icon getIcon(boolean open) {
    return ((SdkType) myProjectJdk.getSdkType()).getIcon();
  }

  @Override
  @Nullable
  @NonNls
  public String getHelpTopic() {
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
  public void setHistory(final History history) {
  }

  @Override
  public ActionCallback navigateTo(@Nullable final Place place, final boolean requestFocus) {
    return mySdkEditor.navigateTo(place, requestFocus);
  }

  @Override
  public void queryPlace(@NotNull final Place place) {
    mySdkEditor.queryPlace(place);
  }
}

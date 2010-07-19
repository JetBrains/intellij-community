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

package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.Sdk;
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
                         final ProjectSdksModel configurable,
                         final Runnable updateTree, @NotNull History history, Project project) {
    super(true, updateTree);
    myProjectJdk = projectJdk;
    mySdkEditor = new SdkEditor(configurable, history, myProjectJdk);
    final StructureConfigurableContext context = ModuleStructureConfigurable.getInstance(project).getContext();
    myProjectStructureElement = new SdkProjectStructureElement(context, myProjectJdk);
  }

  @Override
  public ProjectStructureElement getProjectStructureElement() {
    return myProjectStructureElement;
  }

  public void setDisplayName(final String name) {
    myProjectJdk.setName(name);
  }

  public Sdk getEditableObject() {
    return myProjectJdk;
  }

  public String getBannerSlogan() {
    return ProjectBundle.message("project.roots.jdk.banner.text", myProjectJdk.getName());
  }

  public String getDisplayName() {
    return myProjectJdk.getName();
  }

  public Icon getIcon() {
    return myProjectJdk.getSdkType().getIcon();
  }

  @Nullable
  @NonNls
  public String getHelpTopic() {
    return "preferences.jdks";
  }


  public JComponent createOptionsPanel() {
    return mySdkEditor.createComponent();
  }

  public boolean isModified() {
    return mySdkEditor.isModified();
  }

  public void apply() throws ConfigurationException {
    mySdkEditor.apply();
  }

  public void reset() {
    mySdkEditor.reset();
  }

  public void disposeUIResources() {
    mySdkEditor.disposeUIResources();
  }

  public void setHistory(final History history) {
  }

  public ActionCallback navigateTo(@Nullable final Place place, final boolean requestFocus) {
    return mySdkEditor.navigateTo(place, requestFocus);
  }

  public void queryPlace(@NotNull final Place place) {
    mySdkEditor.queryPlace(place);
  }
}

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
package com.intellij.profile.codeInspection.ui;

import com.intellij.codeInspection.ex.Descriptor;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.scope.packageSet.CustomScopesProviderEx;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

/**
 * @author Dmitry Batkovich
 */
public abstract class ScopesChooser extends ComboBoxAction {

  private final Descriptor myDefaultDescriptor;
  private final InspectionProfileImpl myInspectionProfile;
  private final Project myProject;

  public ScopesChooser(final Descriptor defaultDescriptor, final InspectionProfileImpl inspectionProfile, final Project project) {
    myDefaultDescriptor = defaultDescriptor;
    myInspectionProfile = inspectionProfile;
    myProject = project;
    setPopupTitle("Select a scope to change its settings");
    getTemplatePresentation().setText("In All Scopes");
  }

  @NotNull
  @Override
  protected DefaultActionGroup createPopupActionGroup(final JComponent button) {
    final DefaultActionGroup group = new DefaultActionGroup();

    final List<NamedScope> predefinedScopes = new ArrayList<NamedScope>();
    final List<NamedScope> customScopes = new ArrayList<NamedScope>();
    for (final NamedScopesHolder holder : NamedScopesHolder.getAllNamedScopeHolders(myProject)) {
      Collections.addAll(customScopes, holder.getEditableScopes());
      predefinedScopes.addAll(holder.getPredefinedScopes());
    }
    predefinedScopes.remove(CustomScopesProviderEx.getAllScope());
    fillActionGroup(group, predefinedScopes, myDefaultDescriptor, myInspectionProfile);
    group.addSeparator();
    fillActionGroup(group, customScopes, myDefaultDescriptor, myInspectionProfile);

    //TODO edit scopes order
    //group.addSeparator();
    //group.add(new AnAction("Edit Scopes Order...") {
    //  @Override
    //  public void actionPerformed(final AnActionEvent e) {
    //
    //  }
    //});

    return group;
  }

  protected abstract void onScopeAdded();

  private void fillActionGroup(final DefaultActionGroup group,
                                      final List<NamedScope> scopes,
                                      final Descriptor defaultDescriptor,
                                      final InspectionProfileImpl inspectionProfile) {
    for (final NamedScope scope : scopes) {
      group.add(new AnAction(scope.getName()) {
        @Override
        public void actionPerformed(final AnActionEvent e) {
          inspectionProfile.addScope(defaultDescriptor.getToolWrapper(), scope, defaultDescriptor.getLevel(), true, getEventProject(e));
          onScopeAdded();
        }
      });
    }
  }
}

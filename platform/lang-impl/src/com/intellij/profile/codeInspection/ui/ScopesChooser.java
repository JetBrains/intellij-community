// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.profile.codeInspection.ui;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ex.Descriptor;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.ide.IdeBundle;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.scope.NonProjectFilesScope;
import com.intellij.psi.search.scope.packageSet.CustomScopesProviderEx;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author Dmitry Batkovich
 */
public abstract class ScopesChooser extends ComboBoxAction implements DumbAware {
  private final List<? extends Descriptor> myDefaultDescriptors;
  @NotNull
  private final InspectionProfileImpl myInspectionProfile;
  private final Project myProject;
  private final Set<String> myExcludedScopeNames;

  public ScopesChooser(final List<? extends Descriptor> defaultDescriptors,
                       @NotNull InspectionProfileImpl inspectionProfile,
                       @NotNull Project project,
                       final String[] excludedScopeNames) {
    myDefaultDescriptors = defaultDescriptors;
    myInspectionProfile = inspectionProfile;
    myProject = project;
    myExcludedScopeNames = excludedScopeNames == null ? Collections.emptySet() : ContainerUtil.newHashSet(excludedScopeNames);
    setPopupTitle(LangBundle.message("scopes.chooser.popup.title.select.scope.to.change.its.settings"));
    getTemplatePresentation().setText(InspectionsBundle.messagePointer("action.presentation.ScopesChooser.text"));
  }

  @NotNull
  @Override
  public DefaultActionGroup createPopupActionGroup(@NotNull JComponent component, @NotNull DataContext context) {
    final DefaultActionGroup group = new DefaultActionGroup();

    final List<NamedScope> predefinedScopes = new ArrayList<>();
    final List<NamedScope> customScopes = new ArrayList<>();
    for (final NamedScopesHolder holder : NamedScopesHolder.getAllNamedScopeHolders(myProject)) {
      Collections.addAll(customScopes, holder.getEditableScopes());
      predefinedScopes.addAll(holder.getPredefinedScopes());
    }
    predefinedScopes.remove(CustomScopesProviderEx.getAllScope());
    for (NamedScope predefinedScope : predefinedScopes) {
      if (predefinedScope instanceof NonProjectFilesScope) {
        predefinedScopes.remove(predefinedScope);
        break;
      }
    }

    fillActionGroup(group, predefinedScopes, myDefaultDescriptors, myInspectionProfile, myExcludedScopeNames);
    group.addSeparator();
    fillActionGroup(group, customScopes, myDefaultDescriptors, myInspectionProfile, myExcludedScopeNames);

    group.addSeparator();
    group.add(new DumbAwareAction(IdeBundle.messagePointer("action.Anonymous.text.edit.scopes.order")) {
      @Override
      public void actionPerformed(@NotNull final AnActionEvent e) {
        final ScopesOrderDialog dlg = new ScopesOrderDialog(component, myInspectionProfile, myProject);
        if (dlg.showAndGet()) {
          onScopesOrderChanged();
        }
      }
    });

    return group;
  }

  protected abstract void onScopesOrderChanged();

  protected abstract void onScopeAdded(@NotNull String scopeName);

  private void fillActionGroup(final DefaultActionGroup group,
                               final List<? extends NamedScope> scopes,
                               final List<? extends Descriptor> defaultDescriptors,
                               final InspectionProfileImpl inspectionProfile,
                               final Set<String> excludedScopeNames) {
    for (final NamedScope scope : scopes) {
      final String scopeName = scope.getScopeId();
      if (excludedScopeNames.contains(scopeName)) {
        continue;
      }
      group.add(new DumbAwareAction(scope.getPresentableName()) {
        @Override
        public void actionPerformed(@NotNull final AnActionEvent e) {
          for (final Descriptor defaultDescriptor : defaultDescriptors) {
            InspectionToolWrapper wrapper = defaultDescriptor.getToolWrapper().createCopy();
            wrapper.getTool().readSettings(Descriptor.createConfigElement(defaultDescriptor.getToolWrapper()));
            inspectionProfile.addScope(wrapper, scope, defaultDescriptor.getLevel(), true, e.getProject());
          }
          onScopeAdded(scopeName);
        }
      });
    }
  }

  @Override
  public @NotNull JComponent createCustomComponent(@NotNull Presentation presentation, @NotNull String place) {
    return createComboBoxButton(presentation);
  }
}

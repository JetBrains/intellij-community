/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.ide.util.scopeChooser;

import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.packageDependencies.ChangeListsScopesProvider;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopesCore;
import com.intellij.psi.search.PredefinedSearchScopeProvider;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopeManager;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.ui.ComboboxSpeedSearch;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.ui.ListCellRendererWrapper;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

public class ScopeChooserCombo extends ComboboxWithBrowseButton implements Disposable {
  private Project myProject;
  private boolean mySuggestSearchInLibs;
  private boolean myPrevSearchFiles;
  private NamedScopesHolder.ScopeListener myScopeListener;
  private NamedScopeManager myNamedScopeManager;
  private DependencyValidationManager myValidationManager;
  private boolean myCurrentSelection = true;
  private boolean myUsageView = true;
  private Condition<ScopeDescriptor> myScopeFilter;
  private boolean myShowEmptyScopes = false;

  public ScopeChooserCombo() {
    super(new IgnoringComboBox(){
      @Override
      protected boolean isIgnored(Object item) {
        return item instanceof ScopeSeparator;
      }
    });
  }

  public ScopeChooserCombo(final Project project, boolean suggestSearchInLibs, boolean prevSearchWholeFiles, String preselect) {
    this();
    init(project, suggestSearchInLibs, prevSearchWholeFiles,  preselect);
  }

  public void init(final Project project, final String preselect){
    init(project, false, true, preselect);
  }

  public void init(final Project project, final boolean suggestSearchInLibs, final boolean prevSearchWholeFiles, final String preselect) {
    init(project, suggestSearchInLibs, prevSearchWholeFiles, preselect, null);
  }

  public void init(final Project project,
                   final boolean suggestSearchInLibs,
                   final boolean prevSearchWholeFiles,
                   final String preselect,
                   @Nullable Condition<ScopeDescriptor> scopeFilter) {
    mySuggestSearchInLibs = suggestSearchInLibs;
    myPrevSearchFiles = prevSearchWholeFiles;
    myProject = project;
    myScopeListener = new NamedScopesHolder.ScopeListener() {
      @Override
      public void scopesChanged() {
        final SearchScope selectedScope = getSelectedScope();
        rebuildModel();
        if (selectedScope != null) {
          selectScope(selectedScope.getDisplayName());
        }
      }
    };
    myScopeFilter = scopeFilter;
    myNamedScopeManager = NamedScopeManager.getInstance(project);
    myNamedScopeManager.addScopeListener(myScopeListener);
    myValidationManager = DependencyValidationManager.getInstance(project);
    myValidationManager.addScopeListener(myScopeListener);
    addActionListener(createScopeChooserListener());

    final JComboBox combo = getComboBox();
    combo.setRenderer(new ScopeDescriptionWithDelimiterRenderer());

    rebuildModel();

    selectScope(preselect);
    new ComboboxSpeedSearch(combo) {
      @Override
      protected String getElementText(Object element) {
        if (element instanceof ScopeDescriptor) {
          final ScopeDescriptor descriptor = (ScopeDescriptor)element;
          return descriptor.getDisplay();
        }
        return null;
      }
    };
  }

  public void setCurrentSelection(boolean currentSelection) {
    myCurrentSelection = currentSelection;
  }

  public void setUsageView(boolean usageView) {
    myUsageView = usageView;
  }

  @Override
  public void dispose() {
    super.dispose();
    if (myValidationManager != null) {
      myValidationManager.removeScopeListener(myScopeListener);
      myValidationManager = null;
    }
    if (myNamedScopeManager != null) {
      myNamedScopeManager.removeScopeListener(myScopeListener);
      myNamedScopeManager = null;
    }
    myScopeListener = null;
  }

  private void selectScope(String preselect) {
    if (preselect != null) {
      final JComboBox combo = getComboBox();
      DefaultComboBoxModel model = (DefaultComboBoxModel)combo.getModel();
      for (int i = 0; i < model.getSize(); i++) {
        ScopeDescriptor descriptor = (ScopeDescriptor)model.getElementAt(i);
        if (preselect.equals(descriptor.getDisplay())) {
          combo.setSelectedIndex(i);
          break;
        }
      }
    }
  }

  private ActionListener createScopeChooserListener() {
    return new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final String selection = getSelectedScopeName();
        final EditScopesDialog dlg = EditScopesDialog.showDialog(myProject, selection);
        if (dlg.isOK()){
          rebuildModel();
          final NamedScope namedScope = dlg.getSelectedScope();
          if (namedScope != null) {
            selectScope(namedScope.getName());
          }
        }
      }
    };
  }

  private void rebuildModel() {
    getComboBox().setModel(createModel());
  }

  private DefaultComboBoxModel createModel() {
    final DefaultComboBoxModel model = new DefaultComboBoxModel();

    createPredefinedScopeDescriptors(model);

    final List<NamedScope> changeLists = ChangeListsScopesProvider.getInstance(myProject).getFilteredScopes();
    if (!changeLists.isEmpty()) {
      model.addElement(new ScopeSeparator("VCS Scopes"));
      for (NamedScope changeListScope : changeLists) {
        final GlobalSearchScope scope = GlobalSearchScopesCore.filterScope(myProject, changeListScope);
        addScopeDescriptor(model, new ScopeDescriptor(scope));
      }
    }

    final List<ScopeDescriptor> customScopes = new ArrayList<>();
    final NamedScopesHolder[] holders = NamedScopesHolder.getAllNamedScopeHolders(myProject);
    for (NamedScopesHolder holder : holders) {
      final NamedScope[] scopes = holder.getEditableScopes();  // predefined scopes already included
      for (NamedScope scope : scopes) {
        final GlobalSearchScope searchScope = GlobalSearchScopesCore.filterScope(myProject, scope);
        customScopes.add(new ScopeDescriptor(searchScope));
      }
    }
    if (!customScopes.isEmpty()) {
      model.addElement(new ScopeSeparator("Custom Scopes"));
      for (ScopeDescriptor scope : customScopes) {
        addScopeDescriptor(model, scope);
      }
    }

    return model;
  }

  @Override
  public Dimension getPreferredSize() {
    if (isPreferredSizeSet()) {
      return super.getPreferredSize();
    }
    Dimension preferredSize = super.getPreferredSize();
    return new Dimension(Math.min(400, preferredSize.width), preferredSize.height);
  }

  @Override
  public Dimension getMinimumSize() {
    if (isMinimumSizeSet()) {
      return super.getMinimumSize();
    }
    Dimension minimumSize = super.getMinimumSize();
    return new Dimension(Math.min(200, minimumSize.width), minimumSize.height);
  }

  private void createPredefinedScopeDescriptors(DefaultComboBoxModel model) {
    @SuppressWarnings("deprecation") final DataContext context = DataManager.getInstance().getDataContext();
    for (SearchScope scope : PredefinedSearchScopeProvider.getInstance().getPredefinedScopes(myProject, context, mySuggestSearchInLibs,
                                                                                             myPrevSearchFiles, myCurrentSelection,
                                                                                             myUsageView, myShowEmptyScopes)) {
      addScopeDescriptor(model, new ScopeDescriptor(scope));
    }
    for (ScopeDescriptorProvider provider : Extensions.getExtensions(ScopeDescriptorProvider.EP_NAME)) {
      for (ScopeDescriptor scopeDescriptor : provider.getScopeDescriptors(myProject)) {
        if(myScopeFilter == null || myScopeFilter.value(scopeDescriptor)) {
          model.addElement(scopeDescriptor);
        }
      }
    }
  }

  private void addScopeDescriptor(DefaultComboBoxModel model, ScopeDescriptor scopeDescriptor) {
    if (myScopeFilter == null || myScopeFilter.value(scopeDescriptor)) {
      model.addElement(scopeDescriptor);
    }
  }

  public void setShowEmptyScopes(boolean showEmptyScopes) {
    myShowEmptyScopes = showEmptyScopes;
  }

  @Nullable
  public SearchScope getSelectedScope() {
    final JComboBox combo = getComboBox();
    int idx = combo.getSelectedIndex();
    return idx < 0 ? null : ((ScopeDescriptor)combo.getSelectedItem()).getScope();
  }

  @Nullable
  public String getSelectedScopeName() {
    final JComboBox combo = getComboBox();
    int idx = combo.getSelectedIndex();
    return idx < 0 ? null : ((ScopeDescriptor)combo.getSelectedItem()).getDisplay();
  }

  private static class ScopeSeparator extends ScopeDescriptor {
    private final String myText;

    public ScopeSeparator(final String text) {
      super(null);
      myText = text;
    }

    @Override
    public String getDisplay() {
      return myText;
    }
  }

  private static class ScopeDescriptionWithDelimiterRenderer extends ListCellRendererWrapper<ScopeDescriptor> {
    @Override
    public void customize(JList list, ScopeDescriptor value, int index, boolean selected, boolean hasFocus) {
      setText(value.getDisplay());
      if (value instanceof ScopeSeparator) {
        setSeparator();
      }
    }
  }
}

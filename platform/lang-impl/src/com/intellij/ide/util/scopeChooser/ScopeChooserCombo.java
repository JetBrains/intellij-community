// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.scopeChooser;

import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
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
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
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
  private boolean myShowEmptyScopes;
  private BrowseListener myBrowseListener = null;

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
    myScopeListener = () -> {
      final SearchScope selectedScope = getSelectedScope();
      rebuildModel();
      if (selectedScope != null) {
        selectScope(selectedScope.getDisplayName());
      }
    };
    myScopeFilter = scopeFilter;
    myNamedScopeManager = NamedScopeManager.getInstance(project);
    myNamedScopeManager.addScopeListener(myScopeListener);
    myValidationManager = DependencyValidationManager.getInstance(project);
    myValidationManager.addScopeListener(myScopeListener);
    addActionListener(createScopeChooserListener());

    final ComboBox<ScopeDescriptor> combo = (ComboBox<ScopeDescriptor>)getComboBox();
    combo.setMinimumAndPreferredWidth(JBUI.scale(300));
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

  public void setBrowseListener(BrowseListener browseListener) {
    myBrowseListener = browseListener;
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
    return e -> {
      final String selection = getSelectedScopeName();
      if (myBrowseListener != null) myBrowseListener.onBeforeBrowseStarted();
      final EditScopesDialog dlg = EditScopesDialog.showDialog(myProject, selection);
      if (dlg.isOK()){
        rebuildModel();
        final NamedScope namedScope = dlg.getSelectedScope();
        if (namedScope != null) {
          selectScope(namedScope.getName());
        }
      }
      if (myBrowseListener != null) myBrowseListener.onAfterBrowseFinished();
    };
  }

  private void rebuildModel() {
    getComboBox().setModel(createModel());
  }

  @NotNull
  private DefaultComboBoxModel<ScopeDescriptor> createModel() {
    final DefaultComboBoxModel<ScopeDescriptor> model = new DefaultComboBoxModel<>();

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

  private void createPredefinedScopeDescriptors(@NotNull DefaultComboBoxModel<ScopeDescriptor> model) {
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

  private void addScopeDescriptor(DefaultComboBoxModel<ScopeDescriptor> model, ScopeDescriptor scopeDescriptor) {
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

    ScopeSeparator(@NotNull String text) {
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
      if (value != null) {
        setIcon(value.getDisplayIcon());
        setText(value.getDisplay());
      }
      if (value instanceof ScopeSeparator) {
        setSeparator();
      }
    }
  }

  public interface BrowseListener {
    void onBeforeBrowseStarted();
    void onAfterBrowseFinished();
  }
}

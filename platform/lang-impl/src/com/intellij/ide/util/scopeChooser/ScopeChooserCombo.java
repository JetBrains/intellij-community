// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.scopeChooser;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.popup.ListSeparator;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.psi.search.PredefinedSearchScopeProvider;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopeManager;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.ui.*;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.ui.JBEmptyBorder;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.EnumSet;
import java.util.List;

/**
 * Instances of {@code ScopeChooserCombo} <b>must be disposed</b> when the corresponding dialog or settings page is closed. Otherwise
 * listeners registered in {@code init()} cause memory leak.<br/><br/>
 * Example: if {@code ScopeChooserCombo} is used in a
 * {@code DialogWrapper} subclass, call {@code Disposer.register(getDisposable(), myScopeChooserCombo)}, where
 * {@code getDisposable()} is {@code DialogWrapper}'s method.
 */
public class ScopeChooserCombo extends ComboboxWithBrowseButton implements Disposable {

  private Project myProject;
  private @Nullable Condition<? super ScopeDescriptor> myScopeFilter;
  private BrowseListener myBrowseListener;
  private final ScopeModel scopeModel = new ScopeModel(EnumSet.of(ScopeOption.FROM_SELECTION, ScopeOption.USAGE_VIEW));

  private @Nullable SearchScope preselectedScope;

  public ScopeChooserCombo() {
    super(new ComboBox<>());
  }

  public ScopeChooserCombo(final Project project, boolean suggestSearchInLibs, boolean prevSearchWholeFiles, @Nls String preselect) {
    this();
    init(project, suggestSearchInLibs, prevSearchWholeFiles, preselect, null);
  }

  public void init(final Project project, final @Nls String preselect) {
    init(project, false, true, preselect, null);
  }

  public void init(final Project project,
                   final boolean suggestSearchInLibs,
                   final boolean prevSearchWholeFiles,
                   final Object selection,
                   @Nullable Condition<? super ScopeDescriptor> scopeFilter) {
    initialize(project, suggestSearchInLibs, prevSearchWholeFiles, selection, scopeFilter);
  }

  public @NotNull Promise<?> initialize(@NotNull Project project,
                                        final boolean suggestSearchInLibs,
                                        final boolean prevSearchWholeFiles,
                                        @Nullable Object selection,
                                        @Nullable Condition<? super ScopeDescriptor> scopeFilter) {
    if (myProject != null) {
      throw new IllegalStateException("scope chooser combo already initialized");
    }

    scopeModel.init(project);
    scopeModel.setOption(ScopeOption.LIBRARIES, suggestSearchInLibs);
    scopeModel.setOption(ScopeOption.SEARCH_RESULTS, prevSearchWholeFiles);
    myProject = project;

    NamedScopesHolder.ScopeListener scopeListener = () -> {
      SearchScope selectedScope = getSelectedScope();
      rebuildModelAndSelectScopeOnSuccess(selectedScope);
    };
    myScopeFilter = scopeFilter;
    NamedScopeManager.getInstance(project).addScopeListener(scopeListener, this);
    DependencyValidationManager.getInstance(project).addScopeListener(scopeListener, this);
    addActionListener(this::handleScopeChooserAction);

    ComboBox<ScopeDescriptor> combo = getComboBox();
    combo.setMinimumAndPreferredWidth(JBUIScale.scale(300));
    combo.setRenderer(createRenderer());
    combo.setSwingPopup(false);

    if (selection != null) {
      var provider = PredefinedSearchScopeProvider.getInstance();
      var scopes = provider.getPredefinedScopes(project,
                                                null,
                                                suggestSearchInLibs,
                                                prevSearchWholeFiles,
                                                false,
                                                false,
                                                false);
      for (SearchScope s : scopes) {
        if (selection.equals(s.getDisplayName())) {
          preselectedScope = s;
          break;
        }
      }
    }

    return rebuildModelAndSelectScopeOnSuccess(selection);
  }

  @NotNull
  private ListCellRenderer<ScopeDescriptor> createRenderer() {
    return new GroupedComboBoxRenderer<>(this) {
      @NotNull
      @Override
      public @NlsContexts.ListItem String getText(ScopeDescriptor item) {
        String text = item.getDisplayName();
        return text == null ? super.getText(item) : text;
      }

      @Nullable
      @Override
      public Icon getIcon(ScopeDescriptor item) {
        return item.getIcon();
      }

      @Nullable
      @Override
      public ListSeparator separatorFor(ScopeDescriptor value) {
        String firstElementDisplayName = value.getDisplayName();
        if (firstElementDisplayName != null) return scopeModel.getSeparatorName(value.getDisplayName());
        return null;
      }

      @Override
      public void customize(@NotNull SimpleColoredComponent item,
                            ScopeDescriptor value,
                            int index,
                            boolean isSelected,
                            boolean cellHasFocus) {
        if (value == null) return;
        super.customize(item, value, index, isSelected, cellHasFocus);
      }
    };
  }

  @NotNull
  public static ListCellRenderer<ScopeDescriptor> createDefaultRenderer() {
    return new MyRenderer();
  }

  @Override
  public ComboBox<ScopeDescriptor> getComboBox() {
    //noinspection unchecked
    return (ComboBox<ScopeDescriptor>)super.getComboBox();
  }

  public void setBrowseListener(BrowseListener browseListener) {
    myBrowseListener = browseListener;
  }

  public void setCurrentSelection(boolean currentSelection) {
    scopeModel.setOption(ScopeOption.FROM_SELECTION, currentSelection);
  }

  public void setUsageView(boolean usageView) {
    scopeModel.setOption(ScopeOption.USAGE_VIEW, usageView);
  }

  public void selectItem(@Nullable Object selection) {
    if (selection == null) return;
    JComboBox<ScopeDescriptor> combo = getComboBox();
    DefaultComboBoxModel<ScopeDescriptor> model = (DefaultComboBoxModel<ScopeDescriptor>)combo.getModel();
    for (int i = 0; i < model.getSize(); i++) {
      ScopeDescriptor descriptor = model.getElementAt(i);
      if (selection instanceof String && selection.equals(descriptor.getDisplayName()) ||
          selection instanceof SearchScope && descriptor.scopeEquals((SearchScope)selection)) {
        combo.setSelectedIndex(i);
        break;
      }
    }
  }

  private void handleScopeChooserAction(ActionEvent ignore) {
    String selection = getSelectedScopeName();
    if (myBrowseListener != null) myBrowseListener.onBeforeBrowseStarted();
    EditScopesDialog dlg = EditScopesDialog.showDialog(myProject, selection);
    if (dlg.isOK()) {
      NamedScope namedScope = dlg.getSelectedScope();
      rebuildModelAndSelectScopeOnSuccess(namedScope == null ? null : namedScope.getScopeId());
    }
    if (myBrowseListener != null) myBrowseListener.onAfterBrowseFinished();
  }

  private @NotNull Promise<?> rebuildModelAndSelectScopeOnSuccess(@Nullable Object selection) {
    return scopeModel.getScopeDescriptors(descriptor -> myScopeFilter == null || myScopeFilter.value(descriptor))
      .onSuccess(descriptors -> {
        DefaultComboBoxModel<ScopeDescriptor> model = new DefaultComboBoxModel<>();
        updateModel(model, descriptors);
        getComboBox().setModel(model);
        selectItem(selection);
        preselectedScope = null;
      });
  }

  @RequiresEdt
  protected void updateModel(@NotNull DefaultComboBoxModel<ScopeDescriptor> model,
                             @NotNull List<? extends ScopeDescriptor> descriptors) {
    for (ScopeDescriptor descriptor : descriptors) {
      if (!(descriptor instanceof ScopeSeparator)) {
        model.addElement(descriptor);
      }
    }
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

  public void setShowEmptyScopes(boolean showEmptyScopes) {
    scopeModel.setOption(ScopeOption.EMPTY_SCOPES, showEmptyScopes);
  }

  @Nullable
  public SearchScope getSelectedScope() {
    ScopeDescriptor item = (ScopeDescriptor)getComboBox().getSelectedItem();
    return item == null ? preselectedScope : item.getScope();
  }

  public @Nullable @Nls String getSelectedScopeName() {
    ScopeDescriptor item = (ScopeDescriptor)getComboBox().getSelectedItem();
    if (item == null) {
      return preselectedScope == null ? null : preselectedScope.getDisplayName();
    }
    return item.getDisplayName();
  }

  public @Nullable @NonNls String getSelectedScopeId() {
    ScopeDescriptor item = (ScopeDescriptor)getComboBox().getSelectedItem();
    String scopeName;
    if (item != null) {
      scopeName = item.getDisplayName();
    }
    else {
      if (preselectedScope != null) {
        scopeName = preselectedScope.getDisplayName();
      } else {
        scopeName = null;
      }
    }
    return scopeName != null ? ScopeIdMapper.getInstance().getScopeSerializationId(scopeName) : null;
  }

  private static final class MyRenderer extends SimpleListCellRenderer<ScopeDescriptor> {
    final TitledSeparator separator = new TitledSeparator();

    @Override
    public void customize(@NotNull JList<? extends ScopeDescriptor> list, ScopeDescriptor value, int index, boolean selected, boolean hasFocus) {
      if (value == null) return;
      setIcon(value.getIcon());
      setText(value.getDisplayName());
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends ScopeDescriptor> list,
                                                  ScopeDescriptor value,
                                                  int index,
                                                  boolean selected,
                                                  boolean hasFocus) {
      if (value instanceof ScopeSeparator) {
        separator.setText(value.getDisplayName());
        separator.setBorder(index == -1 ? null : new JBEmptyBorder(UIUtil.DEFAULT_VGAP, 2, UIUtil.DEFAULT_VGAP, 0));
        return separator;
      }
      return super.getListCellRendererComponent(list, value, index, selected, hasFocus);
    }
  }

  public interface BrowseListener {
    void onBeforeBrowseStarted();

    void onAfterBrowseFinished();
  }
}

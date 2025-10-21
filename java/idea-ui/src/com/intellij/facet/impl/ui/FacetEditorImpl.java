// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.facet.impl.ui;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetConfiguration;
import com.intellij.facet.ui.*;
import com.intellij.ide.JavaUiBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.options.UnnamedConfigurableGroup;
import com.intellij.openapi.roots.ProjectModelExternalSource;
import com.intellij.openapi.roots.ui.configuration.ModificationOfImportedModelWarningComponent;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.TabbedPaneWrapper;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.util.HashSet;
import java.util.Set;

@ApiStatus.Internal
public class FacetEditorImpl extends UnnamedConfigurableGroup implements UnnamedConfigurable, FacetEditor {
  private final FacetEditorTab[] myEditorTabs;
  private final FacetErrorPanel myErrorPanel;
  private JComponent myComponent;
  private @Nullable TabbedPaneWrapper myTabbedPane;
  private final FacetEditorContext myContext;
  private final Set<FacetEditorTab> myVisitedTabs = new HashSet<>();
  private int mySelectedTabIndex = 0;
  private final Disposable myDisposable = Disposer.newDisposable();

  public FacetEditorImpl(final FacetEditorContext context, final FacetConfiguration configuration) {
    myContext = context;
    myErrorPanel = new FacetErrorPanel(myDisposable);
    myEditorTabs = configuration.createEditorTabs(context, myErrorPanel.getValidatorsManager());
    for (Configurable configurable : myEditorTabs) {
      add(configurable);
    }
  }

  @Override
  public void reset() {
    super.reset();
    myErrorPanel.getValidatorsManager().validate();
  }

  public JComponent getComponent() {
    if (myComponent == null) {
      myComponent = createComponent();
    }
    return myComponent;
  }

  @Override
  public JComponent createComponent() {
    final JComponent editorComponent;
    if (myEditorTabs.length > 1) {
      final TabbedPaneWrapper tabbedPane = new TabbedPaneWrapper(myDisposable);
      for (FacetEditorTab editorTab : myEditorTabs) {
        JComponent c = editorTab.createComponent();
        UIUtil.addInsets(c, UIUtil.PANEL_SMALL_INSETS);
        tabbedPane.addTab(editorTab.getDisplayName(), c);
      }
      tabbedPane.addChangeListener(new ChangeListener() {
        @Override
        public void stateChanged(@NotNull ChangeEvent e) {
          myEditorTabs[mySelectedTabIndex].onTabLeaving();
          mySelectedTabIndex = tabbedPane.getSelectedIndex();
          onTabSelected(myEditorTabs[mySelectedTabIndex]);
        }
      });
      editorComponent = tabbedPane.getComponent();
      myTabbedPane = tabbedPane;
    }
    else if (myEditorTabs.length == 1) {
      editorComponent = myEditorTabs[0].createComponent();
      UIUtil.addInsets(editorComponent, JBUI.insets(0, 5, 0, 0));
    }
    else {
      editorComponent = new JPanel();
    }
    ProjectModelExternalSource externalSource = myContext.getFacet().getExternalSource();
    if (externalSource != null) {
      myErrorPanel.getValidatorsManager().registerValidator(new FacetEditorValidator() {
        @Override
        public @NotNull ValidationResult check() {
          if (isModified()) {
            String text = ModificationOfImportedModelWarningComponent.getWarningText(
              JavaUiBundle.message("facet.banner.text", myContext.getFacetName()), externalSource);
            return new ValidationResult(text);
          }
          return ValidationResult.OK;
        }
      }, editorComponent);
    }


    final JComponent errorComponent = myErrorPanel.getComponent();
    UIUtil.addInsets(errorComponent, JBUI.insets(0, 5, 5, 0));

    final JPanel panel = new JPanel(new BorderLayout());
    panel.add(BorderLayout.CENTER, editorComponent);
    panel.add(BorderLayout.SOUTH, errorComponent);
    return panel;
  }

  private void onTabSelected(final FacetEditorTab selectedTab) {
    selectedTab.onTabEntering();
    if (myVisitedTabs.add(selectedTab)) {
      final JComponent preferredFocusedComponent = selectedTab.getPreferredFocusedComponent();
      if (preferredFocusedComponent != null) {
        ApplicationManager.getApplication().invokeLater(() -> {
          if (preferredFocusedComponent.isShowing()) {
            IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(preferredFocusedComponent, true));
          }
        });
      }
    }
  }

  @Override
  public void disposeUIResources() {
    Disposer.dispose(myDisposable);
    myErrorPanel.disposeUIResources();
    super.disposeUIResources();
  }

  public @Nullable String getHelpTopic() {
    return 0 <= mySelectedTabIndex && mySelectedTabIndex < myEditorTabs.length ? myEditorTabs[mySelectedTabIndex].getHelpTopic() : null;
  }

  public void onFacetAdded(@NotNull Facet facet) {
    for (FacetEditorTab editorTab : myEditorTabs) {
      editorTab.onFacetInitialized(facet);
    }
  }

  public void setSelectedTabName(final String tabName) {
    getComponent();
    final TabbedPaneWrapper tabbedPane = myTabbedPane;
    if (tabbedPane == null) return;
    for (int i = 0; i < tabbedPane.getTabCount(); i++) {
      if (tabName.equals(tabbedPane.getTitleAt(i))) {
        tabbedPane.setSelectedIndex(i);
        return;
      }
    }
  }

  public FacetEditorContext getContext() {
    return myContext;
  }

  public void onFacetSelected() {
    if (mySelectedTabIndex < myEditorTabs.length) {
      onTabSelected(myEditorTabs[mySelectedTabIndex]);
    }
  }

  @Override
  public FacetEditorTab[] getEditorTabs() {
    return myEditorTabs;
  }

  @Override
  public <T extends FacetEditorTab> T getEditorTab(final @NotNull Class<T> aClass) {
    for (FacetEditorTab editorTab : myEditorTabs) {
      if (aClass.isInstance(editorTab)) {
        return aClass.cast(editorTab);
      }
    }
    return null;
  }
}

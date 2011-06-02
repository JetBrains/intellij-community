/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.ide.util.gotoByName;

import com.intellij.ide.util.ElementsChooser;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * This class contains UI related to filtering functionality.
 */
public abstract class ChooseByNameFilter<T> {
  /**
   * an icon to use
   */
  private static final Icon FILTER_ICON = IconLoader.getIcon("/icons/inspector/useFilter.png");
  /**
   * a parent popup
   */
  private final ChooseByNamePopup myParentPopup;
  /**
   * action toolbar
   */
  private final ActionToolbar myToolbar;
  /**
   * a file type chooser, only one instance is used
   */
  private final ElementsChooser<T> myChooser;
  /**
   * A panel that contains chooser
   */
  private final JPanel myChooserPanel;
  /**
   * a file type popup, the value is non-null if popup is active
   */
  private JBPopup myPopup;
  /**
   * a project to use. The project is used for dimension service.
   */
  private final Project myProject;

  /**
   * A constuctor
   *
   * @param popup               a parent popup
   * @param model               a model for popup
   * @param filterConfiguration storage for selected filter values
   * @param project             a context project
   */
  public ChooseByNameFilter(@NotNull ChooseByNamePopup popup,
                            @NotNull FilteringGotoByModel<T> model,
                            @NotNull ChooseByNameFilterConfiguration<T> filterConfiguration,
                            @NotNull Project project) {
    myParentPopup = popup;
    DefaultActionGroup actionGroup = new DefaultActionGroup("go.to.file.filter", false);
    ToggleAction action = new ToggleAction("Filter", "Filter files by type", FILTER_ICON) {
      public boolean isSelected(final AnActionEvent e) {
        return myPopup != null;
      }

      public void setSelected(final AnActionEvent e, final boolean state) {
        if (state) {
          createPopup();
        }
        else {
          close();
        }
      }
    };
    actionGroup.add(action);
    myToolbar = ActionManager.getInstance().createActionToolbar("gotfile.filter", actionGroup, true);
    myToolbar.setLayoutPolicy(ActionToolbar.NOWRAP_LAYOUT_POLICY);
    myToolbar.updateActionsImmediately();
    myToolbar.getComponent().setFocusable(false);
    myToolbar.getComponent().setBorder(null);
    myProject = project;
    myChooser = createChooser(model, filterConfiguration);
    myChooserPanel = createChooserPanel();
    popup.setToolArea(myToolbar.getComponent());
  }

  /**
   * @return a panel with chooser and buttons
   */
  private JPanel createChooserPanel() {
    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    panel.add(myChooser);
    JPanel buttons = new JPanel();
    JButton all = new JButton("All");
    all.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        myChooser.setAllElementsMarked(true);
      }
    });
    buttons.add(all);
    JButton none = new JButton("None");
    none.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        myChooser.setAllElementsMarked(false);
      }
    });
    buttons.add(none);
    JButton invert = new JButton("Invert");
    invert.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        final int count = myChooser.getElementCount();
        for (int i = 0; i < count; i++) {
          T type = myChooser.getElementAt(i);
          myChooser.setElementMarked(type, !myChooser.isElementMarked(type));
        }
      }
    });
    buttons.add(invert);
    panel.add(buttons);
    return panel;
  }

  /**
   * Create a file type chooser
   *
   *
   * @param model a model to update
   * @param filterConfiguration
   * @return a created file chooser
   */
  protected ElementsChooser<T> createChooser(final FilteringGotoByModel<T> model, final ChooseByNameFilterConfiguration<T> filterConfiguration) {
    List<T> elements = new ArrayList<T>(getAllFilterValues());
    final ElementsChooser<T> chooser = new ElementsChooser<T>(elements, true) {
      @Override
      protected String getItemText(@NotNull final T value) {
        return textForFilterValue(value);
      }

      @Override
      protected Icon getItemIcon(final T value) {
        return iconForFilterValue(value);
      }
    };
    chooser.setFocusable(false);
    final int count = chooser.getElementCount();
    for (int i = 0; i < count; i++) {
      T type = chooser.getElementAt(i);
      if (!DumbService.getInstance(myProject).isDumb() && !filterConfiguration.isFileTypeVisible(type)) {
        chooser.setElementMarked(type, false);
      }
    }
    updateModel(model, chooser, true);
    chooser.addElementsMarkListener(new ElementsChooser.ElementsMarkListener<T>() {
      public void elementMarkChanged(final T element, final boolean isMarked) {
        filterConfiguration.setVisible(element, isMarked);
        updateModel(model, chooser, false);
      }
    });
    return chooser;

  }

  protected abstract String textForFilterValue(T value);

  @Nullable
  protected abstract Icon iconForFilterValue(T value);

  protected abstract Collection<T> getAllFilterValues();

  /**
   * Update model basing on the chooser state
   *
   * @param gotoFileModel a model
   * @param chooser       a file type chooser
   */
  protected void updateModel(final FilteringGotoByModel<T> gotoFileModel, ElementsChooser<T> chooser, boolean initial) {
    final List<T> markedElements = chooser.getMarkedElements();
    gotoFileModel.setFilterItems(markedElements);
    myParentPopup.rebuildList(initial);
  }

  /**
   * Create and show popup
   */
  private void createPopup() {
    if (myPopup != null) {
      return;
    }
    myPopup = JBPopupFactory.getInstance().createComponentPopupBuilder(myChooserPanel, myChooser).setModalContext(false).setFocusable(false)
        .setResizable(true).setCancelOnClickOutside(false).setMinSize(new Dimension(200, 200))
        .setDimensionServiceKey(myProject, "GotoFile_FileTypePopup", false).createPopup();
    myPopup.addListener(new JBPopupListener.Adapter() {
      public void onClosed(LightweightWindowEvent event) {
        myPopup = null;
      }
    });
    myPopup.showUnderneathOf(myToolbar.getComponent());
  }

  /**
   * close the file type filter
   */
  public void close() {
    if (myPopup != null) {
      Disposer.dispose(myPopup);
    }
  }
}

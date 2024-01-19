// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.newItemPopup;

import com.intellij.ide.IdeBundle;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.ScrollingUtil;
import com.intellij.ui.components.JBBox;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.render.RenderingUtil;
import com.intellij.util.ui.JBEmptyBorder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.accessibility.ScreenReader;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class NewItemWithTemplatesPopupPanel<T> extends NewItemSimplePopupPanel {

  protected final JList<T> myTemplatesList;

  private final MyListModel myTemplatesListModel;
  private final Box templatesListHolder;

  private final Collection<TemplatesListVisibilityListener> myVisibilityListeners = new ArrayList<>();

  public NewItemWithTemplatesPopupPanel(List<? extends T> templatesList, ListCellRenderer<T> renderer) {
    this(templatesList, renderer, false);
  }

  public NewItemWithTemplatesPopupPanel(List<? extends T> templatesList, ListCellRenderer<T> renderer, boolean liveErrorValidation) {
    super(liveErrorValidation);
    setBackground(JBUI.CurrentTheme.NewClassDialog.panelBackground());

    myTemplatesListModel = new MyListModel(templatesList);
    myTemplatesList = createTemplatesList(myTemplatesListModel, renderer);
    myTemplatesList.getAccessibleContext().setAccessibleName(IdeBundle.message("action.create.new.class.templates.list.accessible.name"));

    JTextField textField = getTextField();

    if (!ScreenReader.isActive()) {
      ScrollingUtil.installMoveUpAction(myTemplatesList, myTextField);
      ScrollingUtil.installMoveDownAction(myTemplatesList, myTextField);
    }
    else {
      setFocusCycleRoot(true);
      setFocusTraversalPolicy(new LayoutFocusTraversalPolicy());
      textField.addKeyListener(new KeyAdapter() {
        @Override
        public void keyPressed(KeyEvent e) {
          if (e != null && (e.getKeyCode() == KeyEvent.VK_DOWN || e.getKeyCode() == KeyEvent.VK_UP)) {
            textField.transferFocus();
          }
        }
      });
      myTemplatesList.addKeyListener(new KeyAdapter() {
        @Override
        public void keyPressed(KeyEvent e) {
          performApplyActionOnEnter(e);
        }
      });
    }

    JBScrollPane scrollPane = new JBScrollPane(myTemplatesList);
    scrollPane.setBorder(JBUI.Borders.empty());
    scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    templatesListHolder = new JBBox(BoxLayout.Y_AXIS);

    Border lineBorder = JBUI.Borders.customLineTop(JBUI.CurrentTheme.NewClassDialog.bordersColor());
    JBEmptyBorder topMarginBorder = JBUI.Borders.emptyTop(JBUI.CurrentTheme.NewClassDialog.fieldsSeparatorWidth());
    Border outerBorder = JBUI.Borders.merge(topMarginBorder, lineBorder, true);

    if (ExperimentalUI.isNewUI()) {
      templatesListHolder.setOpaque(true);
      templatesListHolder.setBackground(JBUI.CurrentTheme.Popup.BACKGROUND);
      myTemplatesList.setBorder(outerBorder);
    }
    else {
      templatesListHolder.setBorder(JBUI.Borders.merge(lineBorder, outerBorder, true));
    }
    templatesListHolder.add(scrollPane);

    add(templatesListHolder, BorderLayout.CENTER);
  }

  public void addTemplatesVisibilityListener(TemplatesListVisibilityListener listener) {
    myVisibilityListeners.add(listener);
  }

  public void removeTemplatesVisibilityListener(TemplatesListVisibilityListener listener) {
    myVisibilityListeners.remove(listener);
  }

  protected void setTemplatesListVisible(boolean visible) {
    if (templatesListHolder.isVisible() != visible) {
      templatesListHolder.setVisible(visible);
      myVisibilityListeners.forEach(l -> l.visibilityChanged(visible));
    }
  }

  protected void updateTemplatesList(List<? extends T> templatesList) {
    myTemplatesListModel.update(templatesList);
  }

  private @NotNull JBList<T> createTemplatesList(@NotNull ListModel<T> model, ListCellRenderer<T> renderer) {
    JBList<T> list = new JBList<>(model);
    MouseAdapter mouseListener = new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (myApplyAction != null && e.getClickCount() > 1) myApplyAction.consume(e);
      }
    };

    list.addMouseListener(mouseListener);
    list.setCellRenderer(renderer);
    list.setFocusable(ScreenReader.isActive());
    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    list.putClientProperty(RenderingUtil.ALWAYS_PAINT_SELECTION_AS_FOCUSED, !ScreenReader.isActive());
    return list;
  }

  protected final class MyListModel extends AbstractListModel<T> {

    private final List<T> myItems = new ArrayList<>();

    private MyListModel(List<? extends T> items) {
      myItems.addAll(items);
    }

    public void update(List<? extends T> newItems) {
      if (!myItems.isEmpty()) {
        int end = myItems.size() - 1;
        myItems.clear();
        fireIntervalRemoved(this, 0, end);
      }
      if (!newItems.isEmpty()) {
        myItems.addAll(newItems);
        fireIntervalAdded(this, 0, myItems.size() - 1);
      }
    }

    @Override
    public int getSize() {
      return myItems.size();
    }

    @Override
    public T getElementAt(int index) {
      return myItems.get(index);
    }
  }
}

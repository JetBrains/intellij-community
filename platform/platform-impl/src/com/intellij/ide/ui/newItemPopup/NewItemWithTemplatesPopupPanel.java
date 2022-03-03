// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.newItemPopup;

import com.intellij.ui.ScrollingUtil;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.render.RenderingUtil;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
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

  public NewItemWithTemplatesPopupPanel(List<T> templatesList, ListCellRenderer<T> renderer) {
    this(templatesList, renderer, false);
  }

  public NewItemWithTemplatesPopupPanel(List<T> templatesList, ListCellRenderer<T> renderer, boolean liveErrorValidation) {
    super(liveErrorValidation);
    setBackground(JBUI.CurrentTheme.NewClassDialog.panelBackground());

    myTemplatesListModel = new MyListModel(templatesList);
    myTemplatesList = createTemplatesList(myTemplatesListModel, renderer);

    ScrollingUtil.installMoveUpAction(myTemplatesList, myTextField);
    ScrollingUtil.installMoveDownAction(myTemplatesList, myTextField);

    JBScrollPane scrollPane = new JBScrollPane(myTemplatesList);
    scrollPane.setBorder(JBUI.Borders.empty());
    scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    templatesListHolder = new Box(BoxLayout.Y_AXIS);
    Border border = JBUI.Borders.merge(
      JBUI.Borders.emptyTop(JBUI.CurrentTheme.NewClassDialog.fieldsSeparatorWidth()),
      JBUI.Borders.customLine(JBUI.CurrentTheme.NewClassDialog.bordersColor(), 1, 0, 0, 0),
      true
    );

    templatesListHolder.setBorder(border);
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

  @NotNull
  private JBList<T> createTemplatesList(@NotNull ListModel<T> model, ListCellRenderer<T> renderer) {
    JBList<T> list = new JBList<>(model);
    MouseAdapter mouseListener = new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (myApplyAction != null && e.getClickCount() > 1) myApplyAction.consume(e);
      }
    };

    list.addMouseListener(mouseListener);
    list.setCellRenderer(renderer);
    list.setFocusable(false);
    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    list.putClientProperty(RenderingUtil.ALWAYS_PAINT_SELECTION_AS_FOCUSED, true);

    Border border = JBUI.Borders.merge(
      JBUI.Borders.emptyLeft(JBUIScale.scale(5)),
      JBUI.Borders.customLine(JBUI.CurrentTheme.NewClassDialog.bordersColor(), 1, 0, 0, 0),
      true
    );
    list.setBorder(border);
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

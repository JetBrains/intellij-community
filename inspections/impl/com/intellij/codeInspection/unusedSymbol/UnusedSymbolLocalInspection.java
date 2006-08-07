/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInspection.unusedSymbol;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ex.UnfairLocalInspectionTool;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.JDOMExternalizableStringList;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.ReorderableListController;
import com.intellij.ui.ScrollPaneFactory;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListDataListener;
import javax.swing.event.ListDataEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * User: anna
 * Date: 17-Feb-2006
 */
public class UnusedSymbolLocalInspection extends UnfairLocalInspectionTool {
  @NonNls public static final String SHORT_NAME = "UNUSED_SYMBOL";
  @NonNls public static final String DISPLAY_NAME = InspectionsBundle.message("unused.symbol");
  @NonNls public static final String ID = "UnusedDeclaration";

  public boolean LOCAL_VARIABLE = true;
  public boolean FIELD = true;
  public boolean METHOD = true;
  public boolean CLASS = true;
  public boolean PARAMETER = true;
  public JDOMExternalizableStringList INJECTION_ANNOS = new JDOMExternalizableStringList();

  public String getGroupDisplayName() {
    return "";
  }

  public String getDisplayName() {
    return DISPLAY_NAME;
  }

  @NonNls
  public String getShortName() {
    return SHORT_NAME;
  }


  @NonNls
  public String getID() {
    return ID;
  }

  @Nullable
  public JComponent createOptionsPanel() {
    JPanel panel = new JPanel(new GridLayout(5, 1, 2, 2));
    final JCheckBox local = new JCheckBox(InspectionsBundle.message("inspection.unused.symbol.option"), LOCAL_VARIABLE);
    final JCheckBox field = new JCheckBox(InspectionsBundle.message("inspection.unused.symbol.option1"), FIELD);
    final JCheckBox method = new JCheckBox(InspectionsBundle.message("inspection.unused.symbol.option2"), METHOD);
    final JCheckBox classes = new JCheckBox(InspectionsBundle.message("inspection.unused.symbol.option3"), CLASS);
    final JCheckBox parameters = new JCheckBox(InspectionsBundle.message("inspection.unused.symbol.option4"), PARAMETER);

    final DefaultListModel listModel = new DefaultListModel();
    final JList injectionList = new JList(listModel);
    for (String s : INJECTION_ANNOS) {
      listModel.addElement(s);
    }
    injectionList.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
    final DefaultActionGroup actionGroup = new DefaultActionGroup();
    final ReorderableListController<String> controller = ReorderableListController.create(injectionList, actionGroup);
    controller.addAddAction(InspectionsBundle.message("dependency.injection.add.annotation.class"), new Factory<String>() {
      public String create() {
        return Messages.showInputDialog(InspectionsBundle.message("dependency.injection.annotation.class"), InspectionsBundle.message("dependency.injection.add.annotation.class"), Messages.getQuestionIcon());
      }
    }, true);
    controller.addRemoveAction(InspectionsBundle.message("dependency.injection.remove.annotation.class"));
    injectionList.getModel().addListDataListener(new ListDataListener() {
      public void intervalAdded(ListDataEvent e) {
        listChanged();
      }

      private void listChanged() {
        INJECTION_ANNOS.clear();
        for (int i = 0; i < listModel.getSize(); i++) {
            INJECTION_ANNOS.add((String)listModel.getElementAt(i));
        }
      }

      public void intervalRemoved(ListDataEvent e) {
        listChanged();
      }

      public void contentsChanged(ListDataEvent e) {
        listChanged();
      }
    });
    final JScrollPane listScrollPane = ScrollPaneFactory.createScrollPane(injectionList);
    listScrollPane.setBorder(BorderFactory.createEtchedBorder());
    listScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
    listScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    final FontMetrics fontMetrics = injectionList.getFontMetrics(injectionList.getFont());
    listScrollPane.setPreferredSize(new Dimension(0, fontMetrics.getHeight() * 5));

    final JPanel listPanel = new JPanel(new BorderLayout());
    listPanel.setBorder(BorderFactory.createTitledBorder(InspectionsBundle.message("dependency.injection.annotations.list")));

    listPanel.add(ActionManager.getInstance().createActionToolbar(ActionPlaces.PROJECT_VIEW_TOOLBAR, actionGroup, true).getComponent(), BorderLayout.NORTH);
    listPanel.add(listScrollPane, BorderLayout.SOUTH);
    final ActionListener listener = new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        LOCAL_VARIABLE = local.isSelected();
        CLASS = classes.isSelected();
        FIELD = field.isSelected();
        PARAMETER = parameters.isSelected();
        METHOD = method.isSelected();
      }
    };
    local.addActionListener(listener);
    field.addActionListener(listener);
    method.addActionListener(listener);
    classes.addActionListener(listener);
    parameters.addActionListener(listener);
    panel.add(local);
    panel.add(field);
    panel.add(method);
    panel.add(classes);
    panel.add(parameters);

    JPanel doNotExpand = new JPanel(new BorderLayout());
    final JPanel north = new JPanel(new BorderLayout(2, 2));
    north.add(panel, BorderLayout.NORTH);
    north.add(listPanel, BorderLayout.SOUTH);
    doNotExpand.add(north, BorderLayout.NORTH);
    return doNotExpand;
  }
}

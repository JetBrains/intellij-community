/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInspection.unusedSymbol;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.UnfairLocalInspectionTool;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.JDOMExternalizableStringList;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.ReorderableListController;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SortedListModel;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;

/**
 * User: anna
 * Date: 17-Feb-2006
 */
public class UnusedSymbolLocalInspection extends UnfairLocalInspectionTool {
  public static final Collection<String> STANDARD_INJECTION_ANNOS = Collections.unmodifiableCollection(new HashSet<String>(Arrays.asList(
    "javax.annotation.Resource", "javax.ejb.EJB", "javax.xml.ws.WebServiceRef", "javax.persistence.PersistenceContext",
    "javax.persistence.PersistenceUnit")));

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

    final SortedListModel<String> listModel = new SortedListModel<String>(new Comparator<String>() {
      public int compare(final String o1, final String o2) {
        return o1.compareTo(o2);
      }
    });
    final JList injectionList = new JList(listModel);
    for (String s : INJECTION_ANNOS) {
      listModel.add(s);
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

  public IntentionAction createAddToInjectionAnnotationsIntentionAction(final String qualifiedName, final PsiElement context) {
    return new IntentionAction() {
      @NotNull
      public String getText() {
        return QuickFixBundle.message("fix.unused.symbol.injection.text", qualifiedName);
      }

      @NotNull
      public String getFamilyName() {
        return QuickFixBundle.message("fix.unused.symbol.injection.family");
      }

      public boolean isAvailable(Project project, Editor editor, PsiFile file) {
        return true;
      }

      public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        INJECTION_ANNOS.add(qualifiedName);
        Collections.sort(INJECTION_ANNOS);
        final InspectionProfile inspectionProfile = InspectionProjectProfileManager.getInstance(project).getInspectionProfile(context);
        //correct save settings
        ((InspectionProfileImpl)inspectionProfile).isProperSetting(HighlightDisplayKey.find(SHORT_NAME));
        inspectionProfile.save();
      }

      public boolean startInWriteAction() {
        return true;
      }
    };
  }
}

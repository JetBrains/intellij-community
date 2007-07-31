/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInspection.unusedSymbol;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ex.UnfairLocalInspectionTool;
import com.intellij.codeInspection.util.SpecialAnnotationsUtil;
import com.intellij.openapi.util.JDOMExternalizableStringList;
import com.intellij.psi.PsiElement;
import com.intellij.ui.ScrollPaneFactory;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;

/**
 * User: anna
 * Date: 17-Feb-2006
 */
public class UnusedSymbolLocalInspection extends UnfairLocalInspectionTool {
  public static final Collection<String> STANDARD_INJECTION_ANNOS = Collections.unmodifiableCollection(new HashSet<String>(Arrays.asList(
    "javax.annotation.Resource", "javax.ejb.EJB", "javax.xml.ws.WebServiceRef", "javax.persistence.PersistenceContext",
    "javax.persistence.PersistenceUnit", "javax.persistence.GeneratedValue")));

  @NonNls public static final String SHORT_NAME = "UNUSED_SYMBOL";
  @NonNls public static final String DISPLAY_NAME = InspectionsBundle.message("unused.symbol");
  @NonNls public static final String ID = "UnusedDeclaration";

  public boolean LOCAL_VARIABLE = true;
  public boolean FIELD = true;
  public boolean METHOD = true;
  public boolean CLASS = true;
  public boolean PARAMETER = true;
  public JDOMExternalizableStringList INJECTION_ANNOS = new JDOMExternalizableStringList();

  @NotNull
  public String getGroupDisplayName() {
    return GroupNames.DECLARATION_REDUNDANCY;
  }

  @NotNull
  public String getDisplayName() {
    return DISPLAY_NAME;
  }

  @NotNull
  @NonNls
  public String getShortName() {
    return SHORT_NAME;
  }


  @NotNull
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

    String title = InspectionsBundle.message("dependency.injection.annotations.list");
    final JPanel listPanel = SpecialAnnotationsUtil.createSpecialAnnotationsListControl(INJECTION_ANNOS, title);

    JPanel doNotExpand = new JPanel(new BorderLayout());
    final JPanel north = new JPanel(new BorderLayout(2, 2));
    north.add(panel, BorderLayout.NORTH);
    north.add(listPanel, BorderLayout.SOUTH);
    doNotExpand.add(north, BorderLayout.NORTH);

    final JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(doNotExpand);
    scrollPane.setBorder(BorderFactory.createEtchedBorder());
    return scrollPane;
  }

  public IntentionAction createQuickFix(final String qualifiedName, final PsiElement context) {
    return SpecialAnnotationsUtil.createAddToSpecialAnnotationsListIntentionAction(
      QuickFixBundle.message("fix.unused.symbol.injection.text", qualifiedName),
      QuickFixBundle.message("fix.unused.symbol.injection.family"),
      INJECTION_ANNOS, qualifiedName, context);
  }
}

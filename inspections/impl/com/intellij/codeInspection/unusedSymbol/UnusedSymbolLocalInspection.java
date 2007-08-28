/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInspection.unusedSymbol;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ex.UnfairLocalInspectionTool;
import com.intellij.codeInspection.util.SpecialAnnotationsUtil;
import com.intellij.openapi.util.JDOMExternalizableStringList;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
  public boolean REPORT_PARAMETER_FOR_PUBLIC_METHODS = true;
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

  public class OptionsPanel {
    private JCheckBox myCheckLocalVariablesCheckBox;
    private JCheckBox myCheckClassesCheckBox;
    private JCheckBox myCheckFieldsCheckBox;
    private JCheckBox myCheckMethodsCheckBox;
    private JCheckBox myCheckParametersCheckBox;
    private JCheckBox myReportUnusedParametersInPublics;
    private JPanel myAnnos;
    private JPanel myPanel;

    public OptionsPanel() {
      myCheckLocalVariablesCheckBox.setSelected(LOCAL_VARIABLE);
      myCheckClassesCheckBox.setSelected(CLASS);
      myCheckFieldsCheckBox.setSelected(FIELD);
      myCheckMethodsCheckBox.setSelected(METHOD);
      myCheckParametersCheckBox.setSelected(PARAMETER);
      myReportUnusedParametersInPublics.setSelected(REPORT_PARAMETER_FOR_PUBLIC_METHODS);
      final ActionListener listener = new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          LOCAL_VARIABLE = myCheckLocalVariablesCheckBox.isSelected();
          CLASS = myCheckClassesCheckBox.isSelected();
          FIELD = myCheckFieldsCheckBox.isSelected();
          PARAMETER = myCheckParametersCheckBox.isSelected();
          METHOD = myCheckMethodsCheckBox.isSelected();
          REPORT_PARAMETER_FOR_PUBLIC_METHODS = myReportUnusedParametersInPublics.isSelected();
        }
      };
      myCheckLocalVariablesCheckBox.addActionListener(listener);
      myCheckFieldsCheckBox.addActionListener(listener);
      myCheckMethodsCheckBox.addActionListener(listener);
      myCheckClassesCheckBox.addActionListener(listener);
      myCheckParametersCheckBox.addActionListener(listener);
      myReportUnusedParametersInPublics.addActionListener(listener);

      String title = InspectionsBundle.message("dependency.injection.annotations.list");
      final JPanel listPanel = SpecialAnnotationsUtil.createSpecialAnnotationsListControl(INJECTION_ANNOS, title);

      myAnnos.add(listPanel, BorderLayout.CENTER);
    }

    public JComponent getPanel() {
      return myPanel;
    }
  }

  @Nullable
  public JComponent createOptionsPanel() {
    return new OptionsPanel().getPanel();
  }

  public IntentionAction createQuickFix(final String qualifiedName, final PsiElement context) {
    return SpecialAnnotationsUtil.createAddToSpecialAnnotationsListIntentionAction(
      QuickFixBundle.message("fix.unused.symbol.injection.text", qualifiedName),
      QuickFixBundle.message("fix.unused.symbol.injection.family"),
      INJECTION_ANNOS, qualifiedName, context);
  }
}

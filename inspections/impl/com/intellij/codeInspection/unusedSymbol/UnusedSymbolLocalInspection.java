/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInspection.unusedSymbol;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ex.UnfairLocalInspectionTool;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;

/**
 * User: anna
 * Date: 17-Feb-2006
 */
public class UnusedSymbolLocalInspection extends UnfairLocalInspectionTool {
  @NonNls public static final String SHORT_NAME = "UnusedDeclaration";
  @NonNls public static final String DISPLAY_NAME = InspectionsBundle.message("unused.symbol");

  public boolean LOCAL_VARIABLE = true;
  public boolean FIELD = true;
  public boolean METHOD = true;
  public boolean CLASS = true;
  public boolean PARAMETER = true;


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


  @Nullable
  public JComponent createOptionsPanel() {
   JPanel panel = new JPanel(new GridLayout(5, 1, 2, 2));
   final JCheckBox local = new JCheckBox(InspectionsBundle.message("inspection.unused.symbol.option"), LOCAL_VARIABLE);
   final JCheckBox field = new JCheckBox(InspectionsBundle.message("inspection.unused.symbol.option1"), FIELD);
   final JCheckBox method = new JCheckBox(InspectionsBundle.message("inspection.unused.symbol.option2"), METHOD);
   final JCheckBox classes = new JCheckBox(InspectionsBundle.message("inspection.unused.symbol.option3"), CLASS);
   final JCheckBox parameters = new JCheckBox(InspectionsBundle.message("inspection.unused.symbol.option4"), PARAMETER);
   ChangeListener listener = new ChangeListener() {
     public void stateChanged(ChangeEvent e) {
       LOCAL_VARIABLE = local.isSelected();
       CLASS = classes.isSelected();
       FIELD = field.isSelected();
       PARAMETER = parameters.isSelected();
       METHOD = method.isSelected();
     }
   };
   local.addChangeListener(listener);
   field.addChangeListener(listener);
   method.addChangeListener(listener);
   classes.addChangeListener(listener);
   parameters.addChangeListener(listener);
   panel.add(local);
   panel.add(field);
   panel.add(method);
   panel.add(classes);
   panel.add(parameters);

   JPanel doNotExpand = new JPanel(new BorderLayout());
   doNotExpand.add(panel, BorderLayout.NORTH);
   return doNotExpand;
 }
}

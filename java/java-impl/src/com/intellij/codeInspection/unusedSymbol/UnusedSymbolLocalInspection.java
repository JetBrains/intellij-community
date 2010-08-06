/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.codeInspection.unusedSymbol;

import com.intellij.ExtensionPoints;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.BaseJavaLocalInspectionTool;
import com.intellij.codeInspection.reference.EntryPoint;
import com.intellij.codeInspection.ex.UnfairLocalInspectionTool;
import com.intellij.codeInspection.util.SpecialAnnotationsUtil;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.JDOMExternalizableStringList;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.List;

/**
 * User: anna
 * Date: 17-Feb-2006
 */
public class UnusedSymbolLocalInspection extends BaseJavaLocalInspectionTool implements UnfairLocalInspectionTool {
  private static final Collection<String> STANDARD_INJECTION_ANNOS = Collections.unmodifiableCollection(new HashSet<String>(Arrays.asList(
    "javax.annotation.Resource",
    "javax.ejb.EJB",
    "javax.xml.ws.WebServiceRef",
    "javax.persistence.PersistenceContext",
    "javax.persistence.PersistenceUnit",
    "javax.persistence.GeneratedValue")));

  private static List<String> ANNOTATIONS = null;

  @NonNls public static final String SHORT_NAME = HighlightInfoType.UNUSED_SYMBOL_SHORT_NAME;
  @NonNls public static final String DISPLAY_NAME = HighlightInfoType.UNUSED_SYMBOL_DISPLAY_NAME;

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

  @Pattern("[a-zA-Z_0-9.]+")
  @NotNull
  @NonNls
  public String getID() {
    return HighlightInfoType.UNUSED_SYMBOL_ID;
  }

  @Override
  public String getAlternativeID() {
    return "unused";
  }

  public boolean isEnabledByDefault() {
    return true;
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
      myReportUnusedParametersInPublics.setEnabled(PARAMETER);

      final ActionListener listener = new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          LOCAL_VARIABLE = myCheckLocalVariablesCheckBox.isSelected();
          CLASS = myCheckClassesCheckBox.isSelected();
          FIELD = myCheckFieldsCheckBox.isSelected();
          METHOD = myCheckMethodsCheckBox.isSelected();

          PARAMETER = myCheckParametersCheckBox.isSelected();
          REPORT_PARAMETER_FOR_PUBLIC_METHODS = PARAMETER && myReportUnusedParametersInPublics.isSelected();
          myReportUnusedParametersInPublics.setEnabled(PARAMETER);
        }
      };
      myCheckLocalVariablesCheckBox.addActionListener(listener);
      myCheckFieldsCheckBox.addActionListener(listener);
      myCheckMethodsCheckBox.addActionListener(listener);
      myCheckClassesCheckBox.addActionListener(listener);
      myCheckParametersCheckBox.addActionListener(listener);
      myReportUnusedParametersInPublics.addActionListener(listener);

      String title = "Do not check if annotated by";
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

  public IntentionAction createQuickFix(final String qualifiedName, String element) {
    return SpecialAnnotationsUtil.createAddToSpecialAnnotationsListIntentionAction(
      QuickFixBundle.message("fix.unused.symbol.injection.text", element, qualifiedName),
      QuickFixBundle.message("fix.unused.symbol.injection.family"),
      INJECTION_ANNOS, qualifiedName);
  }

  private static List<String> getRegisteredAnnotations() {
    List<String> annotations = ANNOTATIONS;
    if (annotations == null) {
      annotations = new ArrayList<String>();
      for (Object extension : Extensions.getExtensions(ExtensionPoints.DEAD_CODE_TOOL)) {
        final String[] ignoredAnnotations = ((EntryPoint)extension).getIgnoreAnnotations();
        if (ignoredAnnotations != null) {
          ContainerUtil.addAll(annotations, ignoredAnnotations);
        }
      }
      ANNOTATIONS = annotations;
    }
    return annotations;
  }

  public static boolean isInjected(final PsiModifierListOwner modifierListOwner, final UnusedSymbolLocalInspection unusedSymbolInspection) {
    if (modifierListOwner instanceof PsiMethod && !PropertyUtil.isSimplePropertySetter((PsiMethod)modifierListOwner)) {
      return AnnotationUtil.isAnnotated(modifierListOwner, getRegisteredAnnotations()) ||
             AnnotationUtil.isAnnotated(modifierListOwner, unusedSymbolInspection.INJECTION_ANNOS);
    }
    return AnnotationUtil.isAnnotated(modifierListOwner, getRegisteredAnnotations()) ||
           AnnotationUtil.isAnnotated(modifierListOwner, unusedSymbolInspection.INJECTION_ANNOS) ||
           AnnotationUtil.isAnnotated(modifierListOwner, STANDARD_INJECTION_ANNOS);
  }
}

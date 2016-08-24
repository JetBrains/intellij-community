/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInspection.deadCode;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.EntryPointsManagerImpl;
import com.intellij.codeInspection.reference.EntryPoint;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefMethod;
import com.intellij.codeInspection.reference.RefVisitor;
import com.intellij.codeInspection.unusedSymbol.UnusedSymbolLocalInspection;
import com.intellij.codeInspection.unusedSymbol.UnusedSymbolLocalInspectionBase;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class UnusedDeclarationInspection extends UnusedDeclarationInspectionBase {
  private final UnusedParametersInspection myUnusedParameters = new UnusedParametersInspection();

  public UnusedDeclarationInspection() { }

  @TestOnly
  public UnusedDeclarationInspection(boolean enabledInEditor) {
    super(enabledInEditor);
  }

  @Nullable
  @Override
  public String getAlternativeID() {
    return UnusedSymbolLocalInspectionBase.UNUSED_PARAMETERS_SHORT_NAME;
  }

  @Override
  public void runInspection(@NotNull AnalysisScope scope,
                            @NotNull InspectionManager manager,
                            @NotNull GlobalInspectionContext globalContext,
                            @NotNull ProblemDescriptionsProcessor problemDescriptionsProcessor) {
    if (myLocalInspectionBase.PARAMETER) {
      globalContext.getRefManager().iterate(new RefVisitor() {
        @Override public void visitElement(@NotNull RefEntity refEntity) {
          if (!(refEntity instanceof RefMethod) ||
              !globalContext.shouldCheck(refEntity, UnusedDeclarationInspection.this) ||
              !UnusedDeclarationPresentation.compareVisibilities((RefMethod)refEntity, myLocalInspectionBase.getParameterVisibility())) {
            return;
          }
          CommonProblemDescriptor[] descriptors = myUnusedParameters.checkElement(refEntity, scope, manager, globalContext, problemDescriptionsProcessor);
          if (descriptors != null) {
            problemDescriptionsProcessor.addProblemElement(refEntity, descriptors);
          }
        }
      });
    }
    super.runInspection(scope, manager, globalContext, problemDescriptionsProcessor);
  }

  @Override
  public boolean queryExternalUsagesRequests(@NotNull InspectionManager manager,
                                             @NotNull GlobalInspectionContext globalContext,
                                             @NotNull ProblemDescriptionsProcessor problemDescriptionsProcessor) {
    final boolean requests = super.queryExternalUsagesRequests(manager, globalContext, problemDescriptionsProcessor);
    if (!requests) {
      myUnusedParameters.queryExternalUsagesRequests(manager, globalContext, problemDescriptionsProcessor);
    }
    return requests;
  }

  @Nullable
  @Override
  public String getHint(@NotNull QuickFix fix) {
    return myUnusedParameters.getHint(fix);
  }

  @Nullable
  @Override
  public QuickFix getQuickFix(String hint) {
    return myUnusedParameters.getQuickFix(hint);
  }

  @SuppressWarnings("deprecation")
  @Override
  protected UnusedSymbolLocalInspectionBase createUnusedSymbolLocalInspection() {
    return new UnusedSymbolLocalInspection();
  }

  @Override
  public JComponent createOptionsPanel() {
    JTabbedPane tabs = new JBTabbedPane(SwingConstants.TOP);
    tabs.add("Entry points", new OptionsPanel());
    tabs.add("Members to report", myLocalInspectionBase.createOptionsPanel());
    return tabs;
  }

  private class OptionsPanel extends JPanel {
    private final JCheckBox myMainsCheckbox;
    private final JCheckBox myAppletToEntries;
    private final JCheckBox myServletToEntries;
    private final JCheckBox myNonJavaCheckbox;

    private OptionsPanel() {
      super(new GridBagLayout());
      GridBagConstraints gc = new GridBagConstraints();
      gc.weightx = 1;
      gc.weighty = 0;
      gc.insets = JBUI.insets(0, 20, 2, 0);
      gc.fill = GridBagConstraints.HORIZONTAL;
      gc.anchor = GridBagConstraints.NORTHWEST;

      myMainsCheckbox = new JCheckBox(InspectionsBundle.message("inspection.dead.code.option.main"));
      myMainsCheckbox.setSelected(ADD_MAINS_TO_ENTRIES);
      myMainsCheckbox.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          ADD_MAINS_TO_ENTRIES = myMainsCheckbox.isSelected();
        }
      });

      gc.gridy = 0;
      add(myMainsCheckbox, gc);

      myAppletToEntries = new JCheckBox(InspectionsBundle.message("inspection.dead.code.option.applet"));
      myAppletToEntries.setSelected(ADD_APPLET_TO_ENTRIES);
      myAppletToEntries.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          ADD_APPLET_TO_ENTRIES = myAppletToEntries.isSelected();
        }
      });
      gc.gridy++;
      add(myAppletToEntries, gc);

      myServletToEntries = new JCheckBox(InspectionsBundle.message("inspection.dead.code.option.servlet"));
      myServletToEntries.setSelected(ADD_SERVLET_TO_ENTRIES);
      myServletToEntries.addActionListener(new ActionListener(){
        @Override
        public void actionPerformed(ActionEvent e) {
          ADD_SERVLET_TO_ENTRIES = myServletToEntries.isSelected();
        }
      });
      gc.gridy++;
      add(myServletToEntries, gc);

      for (final EntryPoint extension : myExtensions) {
        if (extension.showUI()) {
          final JCheckBox extCheckbox = new JCheckBox(extension.getDisplayName());
          extCheckbox.setSelected(extension.isSelected());
          extCheckbox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
              extension.setSelected(extCheckbox.isSelected());
            }
          });
          gc.gridy++;
          add(extCheckbox, gc);
        }
      }

      myNonJavaCheckbox =
      new JCheckBox(InspectionsBundle.message("inspection.dead.code.option.external"));
      myNonJavaCheckbox.setSelected(ADD_NONJAVA_TO_ENTRIES);
      myNonJavaCheckbox.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          ADD_NONJAVA_TO_ENTRIES = myNonJavaCheckbox.isSelected();
        }
      });

      gc.gridy++;
      add(myNonJavaCheckbox, gc);

      gc.fill = GridBagConstraints.NONE;
      gc.gridy++;
      gc.weighty = 1;
      final JPanel btnPanel = new JPanel(new VerticalFlowLayout());
      btnPanel.add(EntryPointsManagerImpl.createConfigureClassPatternsButton());
      btnPanel.add(EntryPointsManagerImpl.createConfigureAnnotationsButton());
      add(btnPanel, gc);
    }

  }
}

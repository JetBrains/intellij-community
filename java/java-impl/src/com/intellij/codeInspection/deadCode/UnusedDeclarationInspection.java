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
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.Tools;
import com.intellij.codeInspection.reference.*;
import com.intellij.codeInspection.ui.InspectionToolPresentation;
import com.intellij.codeInspection.unusedSymbol.UnusedSymbolLocalInspection;
import com.intellij.codeInspection.unusedSymbol.UnusedSymbolLocalInspectionBase;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.DefUseUtil;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.JBUI;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

  @Nullable
  @Override
  public RefGraphAnnotator getAnnotator(@NotNull RefManager refManager) {
    return new UnusedVariablesGraphAnnotator(InspectionManager.getInstance(refManager.getProject()), refManager);
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
    tabs.add("Members to report", ScrollPaneFactory.createScrollPane(myLocalInspectionBase.createOptionsPanel(), true));
    tabs.add("Entry points", ScrollPaneFactory.createScrollPane(new OptionsPanel(), true));
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

      gc.fill = GridBagConstraints.HORIZONTAL;
      gc.anchor = GridBagConstraints.NORTHWEST;
      gc.gridx = 0;
      gc.gridy = 0;
      gc.gridwidth = 2;
      add(new JBLabel("When entry points are in test sources, mark callees as:"), gc);
      gc.gridy++;

      final JBRadioButton asEntryPoint = new JBRadioButton("used", isTestEntryPoints());
      final JBRadioButton asUnused = new JBRadioButton("unused", !isTestEntryPoints());
      final ButtonGroup group = new ButtonGroup();
      group.add(asEntryPoint);
      group.add(asUnused);
      final ActionListener listener = e -> setTestEntryPoints(asEntryPoint.isSelected());
      asEntryPoint.addActionListener(listener);
      asUnused.addActionListener(listener);

      gc.gridwidth = 1;
      gc.weightx = 0;
      add(asEntryPoint, gc);
      gc.gridx = 1;
      gc.weightx = 1;
      add(asUnused, gc);

      gc.gridx = 0;
      gc.gridy++;

      gc.gridwidth = 2;
      add(new TitledSeparator(), gc);
      gc.gridy++;
      add(new JBLabel("Entry points:"), gc);
      gc.insets = JBUI.insets(5, 0, 0, 0);
      gc.gridy++;

      add(createBtnPanel(), gc);
      gc.gridy++;
      gc.insets = JBUI.insets(0, 5, 2, 0);

      myMainsCheckbox = new JCheckBox(InspectionsBundle.message("inspection.dead.code.option.main"));
      myMainsCheckbox.setSelected(ADD_MAINS_TO_ENTRIES);
      myMainsCheckbox.addActionListener(e -> ADD_MAINS_TO_ENTRIES = myMainsCheckbox.isSelected());


      add(myMainsCheckbox, gc);
      gc.gridy++;

      myAppletToEntries = new JCheckBox(InspectionsBundle.message("inspection.dead.code.option.applet"));
      myAppletToEntries.setSelected(ADD_APPLET_TO_ENTRIES);
      myAppletToEntries.addActionListener(e -> ADD_APPLET_TO_ENTRIES = myAppletToEntries.isSelected());
      add(myAppletToEntries, gc);
      gc.gridy++;

      myServletToEntries = new JCheckBox(InspectionsBundle.message("inspection.dead.code.option.servlet"));
      myServletToEntries.setSelected(ADD_SERVLET_TO_ENTRIES);
      myServletToEntries.addActionListener(e -> ADD_SERVLET_TO_ENTRIES = myServletToEntries.isSelected());
      add(myServletToEntries, gc);
      gc.gridy++;

      for (final EntryPoint extension : myExtensions) {
        if (extension.showUI()) {
          final JCheckBox extCheckbox = new JCheckBox(extension.getDisplayName());
          extCheckbox.setSelected(extension.isSelected());
          extCheckbox.addActionListener(e -> extension.setSelected(extCheckbox.isSelected()));
          add(extCheckbox, gc);
          gc.gridy++;
        }
      }

      myNonJavaCheckbox =
      new JCheckBox(InspectionsBundle.message("inspection.dead.code.option.external"));
      myNonJavaCheckbox.setSelected(ADD_NONJAVA_TO_ENTRIES);
      myNonJavaCheckbox.addActionListener(e -> ADD_NONJAVA_TO_ENTRIES = myNonJavaCheckbox.isSelected());

      gc.weighty = 1;
      add(myNonJavaCheckbox, gc);
    }

    private JPanel createBtnPanel() {
      final JPanel btnPanel = new JPanel(new GridBagLayout());
      GridBagConstraints constraints = new GridBagConstraints();
      constraints.anchor = GridBagConstraints.NORTHWEST;
      constraints.fill = GridBagConstraints.NONE;
      constraints.weightx = 0;
      btnPanel.add(EntryPointsManagerImpl.createConfigureClassPatternsButton(), constraints);
      constraints.gridx = 1;
      btnPanel.add(EntryPointsManagerImpl.createConfigureAnnotationsButton(), constraints);
      constraints.fill = GridBagConstraints.HORIZONTAL;
      constraints.weightx = 1;
      btnPanel.add(Box.createHorizontalBox(), constraints);
      return btnPanel;
    }
  }

  private class UnusedVariablesGraphAnnotator extends RefGraphAnnotator {
    private final InspectionManager myInspectionManager;
    private final GlobalInspectionContextImpl myContext;
    private final Map<String, Tools> myTools;

    UnusedVariablesGraphAnnotator(InspectionManager inspectionManager, RefManager refManager) {
      myInspectionManager = inspectionManager;
      myContext = (GlobalInspectionContextImpl)((RefManagerImpl)refManager).getContext();
      myTools = myContext.getTools();
    }

    @Override
    public void onReferencesBuild(RefElement refElement) {
      if (refElement instanceof RefClass) {
        PsiClass aClass = ((RefClass)refElement).getElement();
        if (aClass != null) {
          for (PsiClassInitializer initializer : aClass.getInitializers()) {
            findUnusedVariables(initializer.getBody(), refElement, aClass);
          }
        }
      }
      else if (refElement instanceof RefMethod) {
        PsiElement element = refElement.getElement();
        if (element instanceof PsiMethod) {
          PsiCodeBlock body = ((PsiMethod)element).getBody();
          if (body != null) {
            findUnusedVariables(body, refElement, element);
          }
        }
      }
    }

    private void findUnusedVariables(PsiCodeBlock body, RefElement refElement, PsiElement element) {
      Tools tools = myTools.get(getShortName());
      if (tools.isEnabled(element)) {
        InspectionToolWrapper toolWrapper = tools.getInspectionTool(element);
        InspectionToolPresentation presentation = myContext.getPresentation(toolWrapper);
        if (((UnusedDeclarationInspection)toolWrapper.getTool()).getSharedLocalInspectionTool().LOCAL_VARIABLE) {
          List<CommonProblemDescriptor> descriptors = new ArrayList<>();

          final Set<PsiVariable> usedVariables = new THashSet<>();
          List<DefUseUtil.Info> unusedDefs = DefUseUtil.getUnusedDefs(body, usedVariables);

          if (unusedDefs != null && !unusedDefs.isEmpty()) {

            for (DefUseUtil.Info info : unusedDefs) {
              PsiElement parent = info.getContext();
              PsiVariable psiVariable = info.getVariable();

              if (parent instanceof PsiDeclarationStatement || parent instanceof PsiResourceVariable) {
                if (!info.isRead() && !SuppressionUtil.inspectionResultSuppressed(psiVariable, UnusedDeclarationInspection.this)) {
                  descriptors.add(createProblemDescriptor(psiVariable));
                }
              }
            }

          }
          body.accept(new JavaRecursiveElementWalkingVisitor() {
            @Override
            public void visitClass(PsiClass aClass) { }

            @Override
            public void visitLambdaExpression(PsiLambdaExpression expression) {} //todo

            @Override
            public void visitLocalVariable(PsiLocalVariable variable) {
              if (!usedVariables.contains(variable) && variable.getInitializer() == null &&
                  !SuppressionUtil.inspectionResultSuppressed(variable, UnusedDeclarationInspection.this)) {
                descriptors.add(createProblemDescriptor(variable));
              }
            }
          });
          if (!descriptors.isEmpty()) {
            presentation.addProblemElement(refElement, descriptors.toArray(CommonProblemDescriptor.EMPTY_ARRAY));
          }
        }
      }
    }

    private ProblemDescriptor createProblemDescriptor(PsiVariable psiVariable) {
      PsiElement toHighlight = ObjectUtils.notNull(psiVariable.getNameIdentifier(), psiVariable);
      return myInspectionManager.createProblemDescriptor(
        toHighlight,
        InspectionsBundle.message("inspection.unused.assignment.problem.descriptor1", "<code>#ref</code> #loc"), (LocalQuickFix)null,
        ProblemHighlightType.LIKE_UNUSED_SYMBOL, false);
    }
  }
}

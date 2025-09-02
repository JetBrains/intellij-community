// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerBase;
import com.intellij.codeInspection.InspectionEngine;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ex.*;
import com.intellij.codeInspection.reference.RefManagerImpl;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.util.Consumer;
import com.intellij.util.PairProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

class HighlightSuppressedWarningsHandler extends HighlightUsagesHandlerBase<PsiLiteralExpression> {
  private static final Logger LOG = Logger.getInstance(HighlightSuppressedWarningsHandler.class);

  private final PsiAnnotation myTarget;
  private final PsiLiteralExpression mySuppressedExpression;
  private final @NotNull ProperTextRange myPriorityRange;


  HighlightSuppressedWarningsHandler(@NotNull Editor editor,
                                     @NotNull PsiFile psiFile,
                                     @NotNull PsiAnnotation target,
                                     @Nullable PsiLiteralExpression suppressedExpression,
                                     @NotNull ProperTextRange priorityRange) {
    super(editor, psiFile);
    myTarget = target;
    mySuppressedExpression = suppressedExpression;
    myPriorityRange = priorityRange;
  }

  @Override
  public @NotNull List<PsiLiteralExpression> getTargets() {
    List<PsiLiteralExpression> result = new ArrayList<>();
    if (mySuppressedExpression != null) {
      result.add(mySuppressedExpression);
      return result;
    }
    PsiAnnotationParameterList list = myTarget.getParameterList();
    PsiNameValuePair[] attributes = list.getAttributes();
    for (PsiNameValuePair attribute : attributes) {
      PsiAnnotationMemberValue value = attribute.getValue();
      if (value instanceof PsiArrayInitializerMemberValue) {
        PsiAnnotationMemberValue[] initializers = ((PsiArrayInitializerMemberValue)value).getInitializers();
        for (PsiAnnotationMemberValue initializer : initializers) {
          if (initializer instanceof PsiLiteralExpression) {
            result.add((PsiLiteralExpression)initializer);
          }
        }
      }
    }
    return result;
  }

  @Override
  protected void selectTargets(@NotNull List<? extends PsiLiteralExpression> targets,
                               @NotNull Consumer<? super List<? extends PsiLiteralExpression>> selectionConsumer) {
    if (targets.size() == 1) {
      selectionConsumer.consume(targets);
    }
    else {
      JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<PsiLiteralExpression>(
        JavaBundle.message("highlight.suppressed.warnings.choose.inspections"), targets) {
        @Override
        public PopupStep<?> onChosen(PsiLiteralExpression selectedValue, boolean finalChoice) {
          selectionConsumer.consume(Collections.singletonList(selectedValue));
          return FINAL_CHOICE;
        }

        @Override
        public @NotNull String getTextFor(PsiLiteralExpression value) {
          Object o = value.getValue();
          LOG.assertTrue(o instanceof String);
          return (String)o;
        }
      }).showInBestPositionFor(myEditor);
    }
  }

  @Override
  public void computeUsages(@NotNull List<? extends PsiLiteralExpression> targets) {
    Project project = myTarget.getProject();
    PsiElement parent = myTarget.getParent().getParent();
    InspectionProfileImpl inspectionProfile = InspectionProjectProfileManager.getInstance(project).getCurrentProfile();
    for (PsiLiteralExpression target : targets) {
      Object value = target.getValue();
      if (!(value instanceof String)) {
        continue;
      }
      List<InspectionToolWrapper<?, ?>> tools = inspectionProfile.findToolsById((String)value, target);
      if (tools.isEmpty()) {
        continue;
      }

      List<LocalInspectionToolWrapper> toolsCopy = new ArrayList<>(tools.size());
      for (InspectionToolWrapper<?, ?> tool : tools) {
        if (tool instanceof LocalInspectionToolWrapper) {
          toolsCopy.add((LocalInspectionToolWrapper)tool.createCopy());
        }
      }
      if (toolsCopy.isEmpty()) {
        continue;
      }
      GlobalInspectionContextImpl context = ((InspectionManagerEx)InspectionManager.getInstance(project)).createNewGlobalContext();
      for (InspectionToolWrapper<?, ?> toolWrapper : toolsCopy) {
        toolWrapper.initialize(context);
      }
      ((RefManagerImpl)context.getRefManager()).runInsideInspectionReadAction(() -> {
        ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
        if (indicator == null) {
          indicator = new DaemonProgressIndicator();
        }
        Map<LocalInspectionToolWrapper, List<ProblemDescriptor>> map =
          InspectionEngine.inspectEx(toolsCopy, myFile, parent.getTextRange(), myPriorityRange, false, true, false,
                                     indicator, PairProcessor.alwaysTrue());

        for (List<ProblemDescriptor> descriptors : map.values()) {
          for (ProblemDescriptor descriptor : descriptors) {
          PsiElement element = descriptor.getPsiElement();
            if (element != null) {
              addOccurrence(element);
            }
          }
        }
      });
    }
  }
}

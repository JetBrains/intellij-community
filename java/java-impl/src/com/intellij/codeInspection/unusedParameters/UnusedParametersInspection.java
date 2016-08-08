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

/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 24, 2001
 * Time: 2:46:32 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.unusedParameters;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.EntryPointsManager;
import com.intellij.codeInspection.ex.EntryPointsManagerImpl;
import com.intellij.codeInspection.reference.*;
import com.intellij.codeInspection.unusedSymbol.UnusedSymbolLocalInspectionBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiReferenceProcessor;
import com.intellij.psi.search.PsiReferenceProcessorAdapter;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessor;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class UnusedParametersInspection extends GlobalJavaBatchInspectionTool {
  @Override
  @Nullable
  public CommonProblemDescriptor[] checkElement(@NotNull final RefEntity refEntity,
                                                @NotNull final AnalysisScope scope,
                                                @NotNull final InspectionManager manager,
                                                @NotNull final GlobalInspectionContext globalContext,
                                                @NotNull final ProblemDescriptionsProcessor processor) {
    if (refEntity instanceof RefMethod) {
      final RefMethod refMethod = (RefMethod)refEntity;

      if (refMethod.isSyntheticJSP()) return null;

      if (refMethod.isExternalOverride()) return null;

      if (!(refMethod.isStatic() || refMethod.isConstructor()) && !refMethod.getSuperMethods().isEmpty()) return null;

      if ((refMethod.isAbstract() || refMethod.getOwnerClass().isInterface()) && refMethod.getDerivedMethods().isEmpty()) return null;

      if (refMethod.isAppMain()) return null;

      final List<RefParameter> unusedParameters = getUnusedParameters(refMethod);

      if (unusedParameters.isEmpty()) return null;

      if (refMethod.isEntry()) return null;

      final PsiModifierListOwner element = refMethod.getElement();
      if (element != null && EntryPointsManager.getInstance(manager.getProject()).isEntryPoint(element)) return null;

      final List<ProblemDescriptor> result = new ArrayList<>();
      for (RefParameter refParameter : unusedParameters) {
        final PsiParameter parameter = refParameter.getElement();
        final PsiIdentifier psiIdentifier = parameter != null ? parameter.getNameIdentifier() : null;
        if (psiIdentifier != null) {
          result.add(manager.createProblemDescriptor(psiIdentifier,
                                                     refMethod.isAbstract()
                                                     ? InspectionsBundle.message("inspection.unused.parameter.composer")
                                                     : InspectionsBundle.message("inspection.unused.parameter.composer1"),
                                                     new AcceptSuggested(globalContext.getRefManager(), processor, refParameter.toString()),
                                                     ProblemHighlightType.LIKE_UNUSED_SYMBOL, false));
        }
      }
      return result.toArray(new CommonProblemDescriptor[result.size()]);
    }
    return null;
  }

  @Override
  protected boolean queryExternalUsagesRequests(@NotNull final RefManager manager, @NotNull final GlobalJavaInspectionContext globalContext,
                                                @NotNull final ProblemDescriptionsProcessor processor) {
    final Project project = manager.getProject();
    for (RefElement entryPoint : globalContext.getEntryPointsManager(manager).getEntryPoints()) {
      processor.ignoreElement(entryPoint);
    }

    final PsiSearchHelper helper = PsiSearchHelper.SERVICE.getInstance(project);
    final AnalysisScope scope = manager.getScope();
    manager.iterate(new RefJavaVisitor() {
      @Override
      public void visitElement(@NotNull RefEntity refEntity) {
        if (refEntity instanceof RefMethod) {
          RefMethod refMethod = (RefMethod)refEntity;
          final PsiModifierListOwner element = refMethod.getElement();
          if (element instanceof PsiMethod) { //implicit constructors are invisible
            PsiMethod psiMethod = (PsiMethod)element;
            if (!refMethod.isStatic() && !refMethod.isConstructor() && !PsiModifier.PRIVATE.equals(refMethod.getAccessModifier())) {
              final ArrayList<RefParameter> unusedParameters = getUnusedParameters(refMethod);
              if (unusedParameters.isEmpty()) return;
              PsiMethod[] derived = OverridingMethodsSearch.search(psiMethod).toArray(PsiMethod.EMPTY_ARRAY);
              for (final RefParameter refParameter : unusedParameters) {
                if (refMethod.isAbstract() && derived.length == 0) {
                  refParameter.parameterReferenced(false);
                  processor.ignoreElement(refParameter);
                }
                else {
                  int idx = refParameter.getIndex();
                  final boolean[] found = {false};
                  for (int i = 0; i < derived.length && !found[0]; i++) {
                    if (scope == null || !scope.contains(derived[i])) {
                      final PsiParameter[] parameters = derived[i].getParameterList().getParameters();
                      if (parameters.length >= idx) continue;
                      PsiParameter psiParameter = parameters[idx];
                      ReferencesSearch.search(psiParameter, helper.getUseScope(psiParameter), false)
                        .forEach(new PsiReferenceProcessorAdapter(
                          new PsiReferenceProcessor() {
                            @Override
                            public boolean execute(PsiReference element) {
                              refParameter.parameterReferenced(false);
                              processor.ignoreElement(refParameter);
                              found[0] = true;
                              return false;
                            }
                          }));
                    }
                  }
                }
              }
            }
          }
        }
      }
    });
    return false;
  }

  @Override
  @Nullable
  public String getHint(@NotNull final QuickFix fix) {
    return ((AcceptSuggested)fix).getHint();
  }

  @Override
  @Nullable
  public QuickFix getQuickFix(final String hint) {
    return new AcceptSuggested(null, null, hint);
  }

  @Override
  public void compose(@NotNull final StringBuffer buf, @NotNull final RefEntity refEntity, @NotNull final HTMLComposer composer) {
    if (refEntity instanceof RefMethod) {
      final RefMethod refMethod = (RefMethod)refEntity;
      final HTMLJavaHTMLComposer javaComposer = composer.getExtension(HTMLJavaHTMLComposer.COMPOSER);
      javaComposer.appendDerivedMethods(buf, refMethod);
      javaComposer.appendSuperMethods(buf, refMethod);
    }
  }

  public static ArrayList<RefParameter> getUnusedParameters(RefMethod refMethod) {
    boolean checkDeep = !refMethod.isStatic() && !refMethod.isConstructor();
    ArrayList<RefParameter> res = new ArrayList<>();
    RefParameter[] methodParameters = refMethod.getParameters();
    RefParameter[] result = new RefParameter[methodParameters.length];
    System.arraycopy(methodParameters, 0, result, 0, methodParameters.length);

    clearUsedParameters(refMethod, result, checkDeep);

    for (RefParameter parameter : result) {
      if (parameter != null && !((RefElementImpl)parameter).isSuppressed(UnusedSymbolLocalInspectionBase.UNUSED_PARAMETERS_SHORT_NAME, UnusedSymbolLocalInspectionBase.UNUSED_ID)) {
        res.add(parameter);
      }
    }

    return res;
  }

  private static void clearUsedParameters(@NotNull RefMethod refMethod, RefParameter[] params, boolean checkDeep) {
    RefParameter[] methodParms = refMethod.getParameters();

    for (int i = 0; i < methodParms.length; i++) {
      if (methodParms[i].isUsedForReading()) params[i] = null;
    }

    if (checkDeep) {
      for (RefMethod refOverride : refMethod.getDerivedMethods()) {
        clearUsedParameters(refOverride, params, checkDeep);
      }
    }
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionsBundle.message("inspection.unused.parameter.display.name");
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return GroupNames.DECLARATION_REDUNDANCY;
  }

  @Override
  @NotNull
  public String getShortName() {
    return UnusedSymbolLocalInspectionBase.UNUSED_PARAMETERS_SHORT_NAME;
  }

  @Override
  public JComponent createOptionsPanel() {
    final JPanel panel = new JPanel(new GridBagLayout());
    panel.add(EntryPointsManagerImpl.createConfigureAnnotationsButton(),
              new GridBagConstraints(0, 0, 1, 1, 1, 1, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE,
                                     JBUI.emptyInsets(), 0, 0));
    return panel;
  }

  private static class AcceptSuggested implements LocalQuickFix {
    private final RefManager myManager;
    private final String myHint;
    private final ProblemDescriptionsProcessor myProcessor;

    public AcceptSuggested(final RefManager manager, final ProblemDescriptionsProcessor processor, final String hint) {
      myManager = manager;
      myProcessor = processor;
      myHint = hint;
    }

    public String getHint() {
      return myHint;
    }

    @Override
    @NotNull
    public String getName() {
      return InspectionsBundle.message("inspection.unused.parameter.delete.quickfix");
    }

    @Override
    @NotNull
    public String getFamilyName() {
      return getName();
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement psiElement = descriptor.getPsiElement();
      if (!FileModificationService.getInstance().preparePsiElementForWrite(psiElement)) return;
      final PsiParameter psiParameter = PsiTreeUtil.getParentOfType(psiElement, PsiParameter.class);
      final PsiMethod psiMethod = PsiTreeUtil.getParentOfType(psiElement, PsiMethod.class);
      if (psiMethod != null && psiParameter != null) {
        final RefElement refMethod = myManager != null ? myManager.getReference(psiMethod) : null;
        final PsiModificationTracker tracker = psiMethod.getManager().getModificationTracker();
        final long startModificationCount = tracker.getModificationCount();

        removeUnusedParameterViaChangeSignature(psiMethod, psiParameter);
        if (refMethod != null && startModificationCount != tracker.getModificationCount()) {
          myProcessor.ignoreElement(refMethod);
        }
      }
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }

    private static void removeUnusedParameterViaChangeSignature(final PsiMethod psiMethod,
                                                                final PsiParameter parameterToDelete) {
      ArrayList<ParameterInfoImpl> newParameters = new ArrayList<>();
      PsiParameter[] oldParameters = psiMethod.getParameterList().getParameters();
      for (int i = 0; i < oldParameters.length; i++) {
        PsiParameter oldParameter = oldParameters[i];
        if (!oldParameter.equals(parameterToDelete)) {
          newParameters.add(new ParameterInfoImpl(i, oldParameter.getName(), oldParameter.getType()));
        }
      }

      ParameterInfoImpl[] parameterInfos = newParameters.toArray(new ParameterInfoImpl[newParameters.size()]);

      ChangeSignatureProcessor csp = new ChangeSignatureProcessor(psiMethod.getProject(), psiMethod, false, null, psiMethod.getName(),
                                                                  psiMethod.getReturnType(), parameterInfos);

      csp.run();
    }
  }

  @Nullable
  @Override
  public String getAlternativeID() {
    return UnusedSymbolLocalInspectionBase.UNUSED_ID;
  }
}

/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInspection.sameParameterValue;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInsight.daemon.impl.UnusedSymbolUtil;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspectionBase;
import com.intellij.codeInspection.reference.*;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.util.ObjectUtils;
import com.intellij.util.VisibilityUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author max
 */
public class SameParameterValueInspectionBase extends GlobalJavaBatchInspectionTool {
  @PsiModifier.ModifierConstant
  private static final String DEFAULT_HIGHEST_MODIFIER = PsiModifier.PROTECTED;
  @PsiModifier.ModifierConstant
  public String highestModifier = DEFAULT_HIGHEST_MODIFIER;

  @Override
  @Nullable
  public CommonProblemDescriptor[] checkElement(@NotNull RefEntity refEntity,
                                                @NotNull AnalysisScope scope,
                                                @NotNull InspectionManager manager,
                                                @NotNull GlobalInspectionContext globalContext,
                                                @NotNull ProblemDescriptionsProcessor processor) {
    List<ProblemDescriptor> problems = null;
    if (refEntity instanceof RefMethod) {
      final RefMethod refMethod = (RefMethod)refEntity;

      if (refMethod.hasSuperMethods() ||
          VisibilityUtil.compare(refMethod.getAccessModifier(), highestModifier) < 0 ||
          refMethod.isEntry()) return null;

      RefParameter[] parameters = refMethod.getParameters();
      for (RefParameter refParameter : parameters) {
        String value = refParameter.getActualValueIfSame();
        if (value != null) {
          if (!globalContext.shouldCheck(refParameter, this)) continue;
          if (problems == null) problems = new ArrayList<>(1);
          problems.add(registerProblem(manager, refParameter.getElement(), value));
        }
      }
    }

    return problems == null ? null : problems.toArray(new CommonProblemDescriptor[problems.size()]);
  }

  @Override
  protected boolean queryExternalUsagesRequests(@NotNull final RefManager manager, @NotNull final GlobalJavaInspectionContext globalContext,
                                                @NotNull final ProblemDescriptionsProcessor processor) {
    manager.iterate(new RefJavaVisitor() {
      @Override public void visitElement(@NotNull RefEntity refEntity) {
        if (refEntity instanceof RefElement && processor.getDescriptions(refEntity) != null) {
          refEntity.accept(new RefJavaVisitor() {
            @Override public void visitMethod(@NotNull final RefMethod refMethod) {
              globalContext.enqueueMethodUsagesProcessor(refMethod, new GlobalJavaInspectionContext.UsagesProcessor() {
                @Override
                public boolean process(PsiReference psiReference) {
                  processor.ignoreElement(refMethod);
                  return false;
                }
              });
            }
          });
        }
      }
    });

    return false;
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionsBundle.message("inspection.same.parameter.display.name");
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return GroupNames.DECLARATION_REDUNDANCY;
  }

  @Override
  @NotNull
  public String getShortName() {
    return "SameParameterValue";
  }

  @Override
  @Nullable
  public QuickFix getQuickFix(final String hint) {
    if (hint == null) return null;
    final int spaceIdx = hint.indexOf(' ');
    if (spaceIdx == -1 || spaceIdx >= hint.length() - 1) return null; //invalid hint
    final String paramName = hint.substring(0, spaceIdx);
    final String value = hint.substring(spaceIdx + 1);
    return createFix(paramName, value);
  }

  protected LocalQuickFix createFix(String paramName, String value) {
    return null;
  }

  @Override
  @Nullable
  public String getHint(@NotNull final QuickFix fix) {
    return fix.toString();
  }

  @Nullable
  @Override
  public LocalInspectionTool getSharedLocalInspectionTool() {
    return new LocalSameParameterValueInspection(this);
  }

  private class LocalSameParameterValueInspection extends BaseJavaLocalInspectionTool {
    private static final String NOT_CONST = "_NOT_CONST";

    private final SameParameterValueInspectionBase myGlobal;

    private LocalSameParameterValueInspection(SameParameterValueInspectionBase global) {
      myGlobal = global;
    }

    @Override
    public boolean runForWholeFile() {
      return true;
    }

    @Override
    @NotNull
    public String getGroupDisplayName() {
      return myGlobal.getGroupDisplayName();
    }

    @Override
    @NotNull
    public String getDisplayName() {
      return myGlobal.getDisplayName();
    }

    @Override
    @NotNull
    public String getShortName() {
      return myGlobal.getShortName();
    }

    @NotNull
    @Override
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                          boolean isOnTheFly,
                                          @NotNull LocalInspectionToolSession session) {
      return new JavaElementVisitor() {
        private final UnusedDeclarationInspectionBase myDeadCodeTool;

        {
          InspectionProfile profile = InspectionProjectProfileManager.getInstance(holder.getProject()).getCurrentProfile();
          UnusedDeclarationInspectionBase deadCodeTool = (UnusedDeclarationInspectionBase)profile.getUnwrappedTool(UnusedDeclarationInspectionBase.SHORT_NAME, holder.getFile());
          myDeadCodeTool = deadCodeTool == null ? new UnusedDeclarationInspectionBase() : deadCodeTool;
        }

        @Override
        public void visitMethod(PsiMethod method) {
          if (method.isConstructor() || VisibilityUtil.compare(VisibilityUtil.getVisibilityModifier(method.getModifierList()), highestModifier) < 0) return;
          PsiParameter[] parameters = method.getParameterList().getParameters();
          if (parameters.length == 0) return;

          if (myDeadCodeTool.isEntryPoint(method)) return;
          if (!method.getHierarchicalMethodSignature().getSuperSignatures().isEmpty()) return;

          PsiParameter lastParameter = parameters[parameters.length - 1];
          final String[] paramValues;
          final boolean hasVarArg = lastParameter.getType() instanceof PsiEllipsisType;
          if (hasVarArg) {
            if (parameters.length == 1) return;
            paramValues = new String[parameters.length - 1];
          } else {
            paramValues = new String[parameters.length];
          }

          if (UnusedSymbolUtil.processUsages(holder.getProject(), method.getContainingFile(), method, new EmptyProgressIndicator(), null, info -> {
            PsiElement element = info.getElement();

            if (!(element instanceof PsiReferenceExpression)) {
              return false;
            }
            PsiElement parent = element.getParent();
            if (!(parent instanceof PsiMethodCallExpression)) {
              return false;
            }
            PsiMethodCallExpression methodCall = (PsiMethodCallExpression) parent;
            PsiExpression[] arguments = methodCall.getArgumentList().getExpressions();
            if (arguments.length < paramValues.length) return false;

            boolean needFurtherProcess = false;
            for (int i = 0; i < paramValues.length; i++) {
              Object value = paramValues[i];
              final String currentArg = getArgValue(arguments[i]);
              if (value == null) {
                paramValues[i] = currentArg;
                if (currentArg != NOT_CONST) {
                  needFurtherProcess = true;
                }
              } else if (value != NOT_CONST) {
                if (!paramValues[i].equals(currentArg)) {
                  paramValues[i] = NOT_CONST;
                } else {
                  needFurtherProcess = true;
                }
              }
            }

            return needFurtherProcess;
          })) {
            for (int i = 0, length = paramValues.length; i < length; i++) {
              String value = paramValues[i];
              if (value != null && value != NOT_CONST) {
                holder.registerProblem(registerProblem(holder.getManager(), parameters[i], value));
              }
            }
          }
        }
      };
    }

    private String getArgValue(PsiExpression arg) {
      return arg instanceof PsiLiteralExpression ? arg.getText() : NOT_CONST;
    }
  }

  private ProblemDescriptor registerProblem(@NotNull InspectionManager manager,
                                            PsiParameter parameter,
                                            String value) {
    final String name = parameter.getName();
    return manager.createProblemDescriptor(ObjectUtils.notNull(parameter.getNameIdentifier(), parameter),
                                           InspectionsBundle.message("inspection.same.parameter.problem.descriptor",
                                                                     "<code>" + name + "</code>",
                                                                     "<code>" + StringUtil.unquoteString(value) + "</code>"),
                                           createFix(name, value),
                                           ProblemHighlightType.GENERIC_ERROR_OR_WARNING, false);
  }
}

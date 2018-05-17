// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.sameParameterValue;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInsight.daemon.impl.UnusedSymbolUtil;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspectionBase;
import com.intellij.codeInspection.reference.*;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.util.ObjectUtils;
import com.intellij.util.VisibilityUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.intellij.codeInspection.reference.RefParameter.VALUE_IS_NOT_CONST;
import static com.intellij.codeInspection.reference.RefParameter.VALUE_UNDEFINED;

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
        Object value = refParameter.getActualConstValue();
        if (value != VALUE_IS_NOT_CONST && value != RefParameter.VALUE_UNDEFINED) {
          if (!globalContext.shouldCheck(refParameter, this)) continue;
          if (problems == null) problems = new ArrayList<>(1);
          problems.add(registerProblem(manager, refParameter.getElement(), value, refParameter.isUsedForWriting()));
        }
      }
    }

    return problems == null ? null : problems.toArray(CommonProblemDescriptor.EMPTY_ARRAY);
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

  private class LocalSameParameterValueInspection extends AbstractBaseJavaLocalInspectionTool {
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
        private final UnusedDeclarationInspectionBase myDeadCodeTool = UnusedDeclarationInspectionBase.findUnusedDeclarationInspection(holder.getFile());

        @Override
        public void visitMethod(PsiMethod method) {
          if (method.isConstructor() || VisibilityUtil.compare(VisibilityUtil.getVisibilityModifier(method.getModifierList()), highestModifier) < 0) return;

          if (method.hasModifierProperty(PsiModifier.NATIVE)) return;

          PsiParameter[] parameters = method.getParameterList().getParameters();
          if (parameters.length == 0) return;

          if (myDeadCodeTool.isEntryPoint(method)) return;
          if (!method.getHierarchicalMethodSignature().getSuperSignatures().isEmpty()) return;

          PsiParameter lastParameter = parameters[parameters.length - 1];
          final Object[] paramValues;
          final boolean hasVarArg = lastParameter.getType() instanceof PsiEllipsisType;
          if (hasVarArg) {
            if (parameters.length == 1) return;
            paramValues = new Object[parameters.length - 1];
          } else {
            paramValues = new Object[parameters.length];
          }
          Arrays.fill(paramValues, VALUE_UNDEFINED);

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
              final Object currentArg = getArgValue(arguments[i]);
              if (value == VALUE_UNDEFINED) {
                paramValues[i] = currentArg;
                if (currentArg != VALUE_IS_NOT_CONST) {
                  needFurtherProcess = true;
                }
              } else if (value != VALUE_IS_NOT_CONST) {
                if (!Comparing.equal(paramValues[i], currentArg)) {
                  paramValues[i] = VALUE_IS_NOT_CONST;
                } else {
                  needFurtherProcess = true;
                }
              }
            }

            return needFurtherProcess;
          })) {
            for (int i = 0, length = paramValues.length; i < length; i++) {
              Object value = paramValues[i];
              if (value != VALUE_UNDEFINED && value != VALUE_IS_NOT_CONST) {
                holder.registerProblem(registerProblem(holder.getManager(), parameters[i], value, false));
              }
            }
          }
        }
      };
    }

    private Object getArgValue(PsiExpression arg) {
      return RefParameterImpl.getExpressionValue(arg);
    }
  }

  private ProblemDescriptor registerProblem(@NotNull InspectionManager manager,
                                            PsiParameter parameter,
                                            Object value,
                                            boolean usedForWriting) {
    final String name = parameter.getName();
    String shortName;
    String stringPresentation;
    if (value instanceof PsiType) {
      stringPresentation = ((PsiType)value).getCanonicalText() + ".class";
      shortName = ((PsiType)value).getPresentableText() + ".class";
    }
    else {
      if (value instanceof PsiField) {
        stringPresentation = PsiFormatUtil.formatVariable((PsiVariable)value, 
                                                          PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_CONTAINING_CLASS | PsiFormatUtilBase.SHOW_FQ_NAME, 
                                                          PsiSubstitutor.EMPTY);
        shortName = PsiFormatUtil.formatVariable((PsiVariable)value, 
                                                 PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_CONTAINING_CLASS, 
                                                 PsiSubstitutor.EMPTY);
      }
      else {
        stringPresentation = shortName =  String.valueOf(value);
      }
    }
    return manager.createProblemDescriptor(ObjectUtils.notNull(parameter.getNameIdentifier(), parameter),
                                           InspectionsBundle.message("inspection.same.parameter.problem.descriptor",
                                                                     name,
                                                                     StringUtil.unquoteString(shortName)),
                                           usedForWriting || parameter.isVarArgs() ? null : createFix(name, stringPresentation),
                                           ProblemHighlightType.GENERIC_ERROR_OR_WARNING, false);
  }
}

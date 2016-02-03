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
package com.intellij.codeInsight.hint.api.impls;

import com.intellij.codeInsight.AnnotationTargetUtil;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.completion.JavaCompletionUtil;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.javadoc.JavaDocInfoGenerator;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.lang.parameterInfo.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.CompletionParameterTypeInferencePolicy;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.scope.MethodProcessorSetupFailedException;
import com.intellij.psi.scope.PsiConflictResolver;
import com.intellij.psi.scope.processor.MethodCandidatesProcessor;
import com.intellij.psi.scope.processor.MethodResolverProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author Maxim.Mossienko
 */
public class MethodParameterInfoHandler implements ParameterInfoHandlerWithTabActionSupport<PsiExpressionList, Object, PsiExpression>, DumbAware {
  private static final Set<Class> ourArgumentListAllowedParentClassesSet = ContainerUtil.<Class>newHashSet(
    PsiMethodCallExpression.class, PsiNewExpression.class, PsiAnonymousClass.class, PsiEnumConstant.class);

  private static final Set<? extends Class> ourStopSearch = Collections.singleton(PsiMethod.class);

  @Override
  public Object[] getParametersForLookup(LookupElement item, ParameterInfoContext context) {
    final List<? extends PsiElement> elements = JavaCompletionUtil.getAllPsiElements(item);
    return elements != null && !elements.isEmpty() && elements.get(0) instanceof PsiMethod ? elements.toArray() : null;
  }

  @Override
  public Object[] getParametersForDocumentation(final Object p, final ParameterInfoContext context) {
    if (p instanceof MethodCandidateInfo) {
      return ((MethodCandidateInfo)p).getElement().getParameterList().getParameters();
    }
    if (p instanceof PsiMethod) {
      return ((PsiMethod)p).getParameterList().getParameters();
    }
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  @Override
  public boolean couldShowInLookup() {
    return true;
  }

  @Override
  @Nullable
  public PsiExpressionList findElementForParameterInfo(@NotNull final CreateParameterInfoContext context) {
    PsiExpressionList argumentList = findArgumentList(context.getFile(), context.getOffset(), context.getParameterListStart());

    if (argumentList != null) {
      return findMethodsForArgumentList(context, argumentList);
    }
    return null;
  }

  private PsiExpressionList findArgumentList(final PsiFile file, int offset, int parameterStart) {
    PsiExpressionList argumentList = ParameterInfoUtils.findArgumentList(file, offset, parameterStart, this);
    if (argumentList == null) {
      final PsiMethodCallExpression methodCall = ParameterInfoUtils.findParentOfTypeWithStopElements(file, offset, PsiMethodCallExpression.class, PsiMethod.class);

      if (methodCall != null) {
        argumentList = methodCall.getArgumentList();
      }
    }
    return argumentList;
  }

  private static PsiExpressionList findMethodsForArgumentList(final CreateParameterInfoContext context,
                                                              @NotNull final PsiExpressionList argumentList) {

    CandidateInfo[] candidates = getMethods(argumentList);
    if (candidates.length == 0) {
      DaemonCodeAnalyzer.getInstance(context.getProject()).updateVisibleHighlighters(context.getEditor());
      return null;
    }
    context.setItemsToShow(candidates);
    return argumentList;
  }

  @Override
  public void showParameterInfo(@NotNull final PsiExpressionList element, @NotNull final CreateParameterInfoContext context) {
    context.showHint(element, element.getTextRange().getStartOffset(), this);
  }

  @Override
  public PsiExpressionList findElementForUpdatingParameterInfo(@NotNull final UpdateParameterInfoContext context) {
    return findArgumentList(context.getFile(), context.getOffset(), context.getParameterListStart());
  }

  @Override
  public void updateParameterInfo(@NotNull final PsiExpressionList o, @NotNull final UpdateParameterInfoContext context) {
    PsiElement parameterOwner = context.getParameterOwner();
    if (parameterOwner != o) {
      context.removeHint();
      return;
    }

    int index = ParameterInfoUtils.getCurrentParameterIndex(o.getNode(), context.getOffset(), JavaTokenType.COMMA);
    context.setCurrentParameter(index);

    Object[] candidates = context.getObjectsToView();
    PsiExpression[] args = o.getExpressions();
    PsiElement realResolve = null;

    for (int i = 0; i < candidates.length; i++) {
      CandidateInfo candidate = (CandidateInfo)candidates[i];
      PsiMethod method = (PsiMethod)candidate.getElement();
      if (!method.isValid()) continue;
      PsiSubstitutor substitutor = getCandidateInfoSubstitutor(candidate);
      assert substitutor != null;

      if (!method.isValid() || !substitutor.isValid()) {
          // this may sometimes happen e,g, when editing method call in field initializer candidates in the same file get invalidated
        context.setUIComponentEnabled(i, false);
        continue;
      }

      PsiParameter[] parms = method.getParameterList().getParameters();
      boolean enabled = true;
      if (parms.length <= index) {
        if (parms.length > 0) {
          if (method.isVarArgs()) {
            for (int j = 0; j < parms.length - 1; j++) {
              PsiType parmType = substitutor.substitute(parms[j].getType());
              PsiType argType = args[j].getType();
              if (argType != null && !parmType.isAssignableFrom(argType)) {
                enabled = false;
                break;
              }
            }

            if (enabled) {
              PsiArrayType lastParmType = (PsiArrayType)substitutor.substitute(parms[parms.length - 1].getType());
              PsiType componentType = lastParmType.getComponentType();

              if (parms.length == args.length) {
                PsiType lastArgType = args[args.length - 1].getType();
                if (lastArgType != null && !lastParmType.isAssignableFrom(lastArgType) &&
                    !componentType.isAssignableFrom(lastArgType)) {
                  enabled = false;
                }
              }
              else {
                for (int j = parms.length; j <= index && j < args.length; j++) {
                  PsiExpression arg = args[j];
                  PsiType argType = arg.getType();
                  if (argType != null && !componentType.isAssignableFrom(argType)) {
                    enabled = false;
                    break;
                  }
                }
              }
            }
          }
          else {
            enabled = false;
          }
        }
        else {
          enabled = index == 0;
        }
      }
      else {
        enabled = isAssignableParametersBeforeGivenIndex(parms, args, index, substitutor);
      }

      context.setUIComponentEnabled(i, enabled);
      if (candidates.length > 1 &&
          enabled &&
          parms.length == args.length &&
          isAssignableParametersBeforeGivenIndex(parms, args, args.length, substitutor)) {
        if (realResolve == null) {
          PsiCall call = getCall(o);
          if (call != null) realResolve = call.resolveMethod();
          if (realResolve == null) realResolve = PsiUtilCore.NULL_PSI_ELEMENT;
        }
        if (realResolve == PsiUtilCore.NULL_PSI_ELEMENT || realResolve == method) context.setHighlightedParameter(candidate);
      }
    }
  }

  private static PsiSubstitutor getCandidateInfoSubstitutor(CandidateInfo candidate) {
    return candidate instanceof MethodCandidateInfo && ((MethodCandidateInfo)candidate).isInferencePossible()
                                 ? ((MethodCandidateInfo)candidate).inferTypeArguments(CompletionParameterTypeInferencePolicy.INSTANCE, true)
                                 : candidate.getSubstitutor();
  }

  private static boolean isAssignableParametersBeforeGivenIndex(final PsiParameter[] parms,
                                                                final PsiExpression[] args,
                                                                int length,
                                                                PsiSubstitutor substitutor) {
    for (int j = 0; j < length; j++) {
      PsiParameter parm = parms[j];
      PsiExpression arg = args[j];
      assert parm.isValid();
      assert arg.isValid();
      PsiType parmType = parm.getType();
      PsiType argType = arg.getType();
      if (argType == null) continue;
      if (parmType instanceof PsiEllipsisType && parmType.getArrayDimensions() == argType.getArrayDimensions() + 1) {
        parmType = ((PsiEllipsisType)parmType).getComponentType();
      }
      parmType = substitutor.substitute(parmType);

      if (!parmType.isAssignableFrom(argType)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public String getParameterCloseChars() {
    return ParameterInfoUtils.DEFAULT_PARAMETER_CLOSE_CHARS;
  }

  @Override
  public boolean tracksParameterIndex() {
    return true;
  }

  @Override
  @NotNull
  public Class<PsiExpressionList> getArgumentListClass() {
    return PsiExpressionList.class;
  }

  @Override
  @NotNull
  public IElementType getActualParametersRBraceType() {
    return JavaTokenType.RBRACE;
  }

  @Override
  @NotNull
  public Set<Class> getArgumentListAllowedParentClasses() {
    return ourArgumentListAllowedParentClassesSet;
  }

  @NotNull
  @Override
  public Set<? extends Class> getArgListStopSearchClasses() {
    return ourStopSearch;
  }

  @Override
  @NotNull
  public IElementType getActualParameterDelimiterType() {
    return JavaTokenType.COMMA;
  }

  @Override
  @NotNull
  public PsiExpression[] getActualParameters(@NotNull PsiExpressionList psiExpressionList) {
    return psiExpressionList.getExpressions();
  }

  private static PsiCall getCall(PsiExpressionList list) {
    PsiElement listParent = list.getParent();
    if (listParent instanceof PsiMethodCallExpression) {
      return (PsiCall)listParent;
    }
    if (listParent instanceof PsiNewExpression) {
      return (PsiCall)listParent;
    }
    if (listParent instanceof PsiAnonymousClass) {
      return (PsiCall)listParent.getParent();
    }
    if (listParent instanceof PsiEnumConstant) {
      return (PsiCall)listParent;
    }
    return null;
  }

  
  
  private static CandidateInfo[] getMethods(PsiExpressionList argList) {
    final PsiCall call = getCall(argList);
    PsiResolveHelper helper = JavaPsiFacade.getInstance(argList.getProject()).getResolveHelper();

    if (call instanceof PsiCallExpression) {
      CandidateInfo[] candidates = getCandidates((PsiCallExpression)call);
      ArrayList<CandidateInfo> result = new ArrayList<CandidateInfo>();

      if (!(argList.getParent() instanceof PsiAnonymousClass)) {
        cand:
        for (CandidateInfo candidate : candidates) {
          PsiMethod methodCandidate = (PsiMethod)candidate.getElement();

          for (CandidateInfo info : result) {
            if (MethodSignatureUtil.isSuperMethod(methodCandidate, (PsiMethod)info.getElement())) {
              continue cand;
            }
          }
          if (candidate.isStaticsScopeCorrect()) {
            boolean accessible = candidate.isAccessible();
            if (!accessible && methodCandidate.getModifierList().hasModifierProperty(PsiModifier.PRIVATE)) {
              // privates are accessible within one file
              accessible = JavaPsiFacade.getInstance(methodCandidate.getProject()).getResolveHelper()
                .isAccessible(methodCandidate, methodCandidate.getModifierList(), call, null, null);
            }
            if (accessible) result.add(candidate);
          }
        }
      }
      else {
        PsiClass aClass = (PsiClass)argList.getParent();
        for (CandidateInfo candidate : candidates) {
          if (candidate.isStaticsScopeCorrect() && helper.isAccessible((PsiMethod)candidate.getElement(), argList, aClass)) {
            result.add(candidate);
          }
        }
      }
      return result.isEmpty() ? candidates : result.toArray(new CandidateInfo[result.size()]);
    }
    else {
      assert call instanceof PsiEnumConstant;
      //We are inside our own enum, no isAccessible check needed
      PsiMethod[] constructors = ((PsiEnumConstant)call).getContainingClass().getConstructors();
      CandidateInfo[] result = new CandidateInfo[constructors.length];

      for (int i = 0; i < constructors.length; i++) {
        result[i] = new CandidateInfo(constructors[i], PsiSubstitutor.EMPTY);
      }
      return result;
    }
  }

  private static CandidateInfo[] getCandidates(PsiCallExpression call) {
    final MethodCandidatesProcessor processor = new MethodResolverProcessor(call, call.getContainingFile(), new PsiConflictResolver[0]) {
      @Override
      protected boolean acceptVarargs() {
        return false;
      }
    };

    try {
      PsiScopesUtil.setupAndRunProcessor(processor, call, true);
    }
    catch (MethodProcessorSetupFailedException e) {
      return CandidateInfo.EMPTY_ARRAY;
    }
    final List<CandidateInfo> results = processor.getResults();
    return results.toArray(new CandidateInfo[results.size()]);
  }

  public static String updateMethodPresentation(@NotNull PsiMethod method, @Nullable PsiSubstitutor substitutor, @NotNull ParameterInfoUIContext context) {
    CodeInsightSettings settings = CodeInsightSettings.getInstance();

    if (!method.isValid() || substitutor != null && !substitutor.isValid()) {
      context.setUIComponentEnabled(false);
      return null;
    }

    StringBuilder buffer = new StringBuilder();

    if (settings.SHOW_FULL_SIGNATURES_IN_PARAMETER_INFO) {
      if (!method.isConstructor()) {
        PsiType returnType = method.getReturnType();
        if (substitutor != null) {
          returnType = substitutor.substitute(returnType);
        }

        appendModifierList(buffer, method);
        buffer.append(returnType.getPresentableText());
        buffer.append(" ");
      }
      buffer.append(method.getName());
      buffer.append("(");
    }

    final int currentParameter = context.getCurrentParameterIndex();

    PsiParameter[] parms = method.getParameterList().getParameters();
    int numParams = parms.length;
    int highlightStartOffset = -1;
    int highlightEndOffset = -1;
    if (numParams > 0) {
      for (int j = 0; j < numParams; j++) {
        PsiParameter param = parms[j];

        int startOffset = buffer.length();

        if (param.isValid()) {
          PsiType paramType = param.getType();
          assert paramType.isValid();
          if (substitutor != null) {
            assert substitutor.isValid();
            paramType = substitutor.substitute(paramType);
          }
          appendModifierList(buffer, param);
          buffer.append(paramType.getPresentableText());
          String name = param.getName();
          if (name != null) {
            buffer.append(" ");
            buffer.append(name);
          }
        }

        int endOffset = buffer.length();

        if (j < numParams - 1) {
          buffer.append(", ");
        }

        if (context.isUIComponentEnabled() &&
            (j == currentParameter || j == numParams - 1 && param.isVarArgs() && currentParameter >= numParams)) {
          highlightStartOffset = startOffset;
          highlightEndOffset = endOffset;
        }
      }
    }
    else {
      buffer.append(CodeInsightBundle.message("parameter.info.no.parameters"));
    }

    if (settings.SHOW_FULL_SIGNATURES_IN_PARAMETER_INFO) {
      buffer.append(")");
    }

    return context.setupUIComponentPresentation(
      buffer.toString(),
      highlightStartOffset,
      highlightEndOffset,
      !context.isUIComponentEnabled(),
      method.isDeprecated(),
      false,
      context.getDefaultParameterColor()
    );
  }

  private static void appendModifierList(@NotNull StringBuilder buffer, @NotNull PsiModifierListOwner owner) {
    int lastSize = buffer.length();
    Set<String> shownAnnotations = ContainerUtil.newHashSet();
    for (PsiAnnotation annotation : AnnotationUtil.getAllAnnotations(owner, false, null, !DumbService.isDumb(owner.getProject()))) {
      final PsiJavaCodeReferenceElement element = annotation.getNameReferenceElement();
      if (element != null) {
        final PsiElement resolved = element.resolve();
        if (resolved instanceof PsiClass &&
            (!JavaDocInfoGenerator.isDocumentedAnnotationType(resolved) ||
             AnnotationTargetUtil.findAnnotationTarget((PsiClass)resolved, PsiAnnotation.TargetType.TYPE_USE) != null)) {
          continue;
        }

        String referenceName = element.getReferenceName();
        if (shownAnnotations.add(referenceName) || JavaDocInfoGenerator.isRepeatableAnnotationType(resolved)) {
          if (lastSize != buffer.length()) buffer.append(' ');
          buffer.append('@').append(referenceName);
        }
      }
    }
    if (lastSize != buffer.length()) buffer.append(' ');
  }

  @Override
  public void updateUI(final Object p, @NotNull final ParameterInfoUIContext context) {
    if (p instanceof CandidateInfo) {
      CandidateInfo info = (CandidateInfo)p;
      PsiMethod method = (PsiMethod)info.getElement();
      if (!method.isValid()) {
        context.setUIComponentEnabled(false);
        return;
      }

      updateMethodPresentation(method, getCandidateInfoSubstitutor(info), context);
    }
    else {
      updateMethodPresentation((PsiMethod)p, null, context);
    }
  }
}

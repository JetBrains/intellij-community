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
package com.intellij.codeInsight.hint.api.impls;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.completion.JavaCompletionUtil;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.lang.parameterInfo.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * @author Maxim.Mossienko
 */
public class MethodParameterInfoHandler implements ParameterInfoHandlerWithTabActionSupport<PsiExpressionList,Object,PsiExpression>,
                                                   DumbAware {
  private static final Set<Class> ourArgumentListAllowedParentClassesSet = new HashSet<Class>(
      Arrays.asList(PsiMethodCallExpression.class,PsiNewExpression.class, PsiAnonymousClass.class,PsiEnumConstant.class));

  public Object[] getParametersForLookup(LookupElement item, ParameterInfoContext context) {
    final List<? extends PsiElement> allElements = JavaCompletionUtil.getAllPsiElements(item);

    if (allElements != null &&
        allElements.size() > 0 &&
        allElements.get(0) instanceof PsiMethod) {
      return allElements.toArray(new PsiMethod[allElements.size()]);
    }
    return null;
  }

  public Object[] getParametersForDocumentation(final Object p, final ParameterInfoContext context) {
    if (p instanceof MethodCandidateInfo) {
      return ((MethodCandidateInfo)p).getElement().getParameterList().getParameters();
    } else if (p instanceof PsiMethod) {
      return ((PsiMethod)p).getParameterList().getParameters();
    }
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  public boolean couldShowInLookup() {
    return true;
  }

  @Nullable
  public PsiExpressionList findElementForParameterInfo(final CreateParameterInfoContext context) {
    PsiExpressionList argumentList = findArgumentList(context.getFile(), context.getOffset(), context.getParameterListStart());

    if (argumentList != null) {
      return findMethodsForArgumentList(context, argumentList);
    }
    return argumentList;
  }

  private PsiExpressionList findArgumentList(final PsiFile file, int offset, int parameterStart) {
    PsiExpressionList argumentList = ParameterInfoUtils.findArgumentList(file, offset, parameterStart,this);
    if (argumentList == null) {
      final PsiMethodCallExpression methodCall = ParameterInfoUtils.findParentOfType(file, offset, PsiMethodCallExpression.class);

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

  public void showParameterInfo(@NotNull final PsiExpressionList element, final CreateParameterInfoContext context) {
    context.showHint(element, element.getTextRange().getStartOffset(), this);
  }

  public PsiExpressionList findElementForUpdatingParameterInfo(final UpdateParameterInfoContext context) {
    return findArgumentList(context.getFile(), context.getOffset(), context.getParameterListStart());
  }

  public void updateParameterInfo(@NotNull final PsiExpressionList o, final UpdateParameterInfoContext context) {
    if (context.getParameterOwner() != o) {
      context.removeHint();
      return;
    }
    int index = ParameterInfoUtils.getCurrentParameterIndex(o.getNode(), context.getOffset(),JavaTokenType.COMMA);
    context.setCurrentParameter(index);

    Object[] candidates = context.getObjectsToView();
    PsiExpression[] args = o.getExpressions();

    for(int i = 0; i < candidates.length; i++) {
      CandidateInfo candidate = (CandidateInfo) candidates[i];
      PsiMethod method = (PsiMethod) candidate.getElement();
      PsiSubstitutor substitutor = candidate.getSubstitutor();
      assert substitutor != null;

      if (!method.isValid() || !substitutor.isValid()){ // this may sometimes happen e,g, when editing method call in field initializer candidates in the same file get invalidated
        context.setUIComponentEnabled(i, false);
        continue;
      }

      PsiParameter[] parms = method.getParameterList().getParameters();
      boolean enabled = true;
      if (parms.length <= index){
        if (parms.length > 0){
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
                for (int j = parms.length; j <= index && j<args.length; j++) {
                  PsiExpression arg = args[j];
                  PsiType argType = arg.getType();
                  if (argType != null && !componentType.isAssignableFrom(argType)) {
                    enabled = false;
                    break;
                  }
                }
              }
            }
          } else {
            enabled = false;
          }
        }
        else{
          enabled = index == 0;
        }
      }
      else{
        enabled = isAssignableParametersBeforeGivenIndex(parms, args, index, substitutor);
      }

      context.setUIComponentEnabled(i, enabled);
      if (candidates.length > 1 && enabled && parms.length == args.length && isAssignableParametersBeforeGivenIndex(parms,args, args.length, substitutor)) {
        context.setHighlightedParameter(candidate);
      }
    }
  }

  private static boolean isAssignableParametersBeforeGivenIndex(final PsiParameter[] parms, final PsiExpression[] args, int length, PsiSubstitutor substitutor) {
    for(int j = 0; j < length; j++){
      PsiParameter parm = parms[j];
      PsiExpression arg = args[j];
      assert parm.isValid();
      assert arg.isValid();
      PsiType parmType = substitutor.substitute(parm.getType());
      PsiType argType = arg.getType();

      if (argType != null && !parmType.isAssignableFrom(argType)){
        return false;
      }
    }
    return true;
  }

  public String getParameterCloseChars() {
    return ParameterInfoUtils.DEFAULT_PARAMETER_CLOSE_CHARS;
  }

  public boolean tracksParameterIndex() {
    return true;
  }

  @NotNull
  public Class<PsiExpressionList> getArgumentListClass() {
    return PsiExpressionList.class;
  }

  @NotNull
  public IElementType getActualParametersRBraceType() {
    return JavaTokenType.RBRACE;
  }

  @NotNull
  public Set<Class> getArgumentListAllowedParentClasses() {
    return ourArgumentListAllowedParentClassesSet;
  }

  @NotNull
  public IElementType getActualParameterDelimiterType() {
    return JavaTokenType.COMMA;
  }

  @NotNull
  public PsiExpression[] getActualParameters(@NotNull PsiExpressionList psiExpressionList) {
    return psiExpressionList.getExpressions();
  }

  private static PsiCall getCall(PsiExpressionList list){
    if (list.getParent() instanceof PsiMethodCallExpression){
      return (PsiCall)list.getParent();
    }
    else if (list.getParent() instanceof PsiNewExpression){
      return (PsiCall)list.getParent();
    }
    else if (list.getParent() instanceof PsiAnonymousClass){
      return (PsiCall)list.getParent().getParent();
    }
    else if (list.getParent() instanceof PsiEnumConstant){
      return (PsiCall)list.getParent();
    }
    else{
      return null;
    }
  }

  private static CandidateInfo[] getMethods(PsiExpressionList argList) {
    final PsiCall call = getCall(argList);
    PsiResolveHelper helper = JavaPsiFacade.getInstance(argList.getProject()).getResolveHelper();

    if (call instanceof PsiCallExpression) {
      CandidateInfo[] candidates = helper.getReferencedMethodCandidates((PsiCallExpression)call, true);
      ArrayList<CandidateInfo> result = new ArrayList<CandidateInfo>();

      if (!(argList.getParent() instanceof PsiAnonymousClass)) {
        cand: for (CandidateInfo candidate : candidates) {
          for (CandidateInfo info : result) {
            if (MethodSignatureUtil.isSuperMethod((PsiMethod)candidate.getElement(), (PsiMethod)info.getElement())) {
              continue cand;
            }
          }
          if (candidate.isStaticsScopeCorrect() && candidate.isAccessible()) result.add(candidate);
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
      return result.toArray(new CandidateInfo[result.size()]);
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

  public static void updateMethodPresentation(PsiMethod method, PsiSubstitutor substitutor, ParameterInfoUIContext context) {
    CodeInsightSettings settings = CodeInsightSettings.getInstance();

    if (!method.isValid()){
      context.setUIComponentEnabled(false);
      return;
    }

    StringBuilder buffer = new StringBuilder();

    if (settings.SHOW_FULL_SIGNATURES_IN_PARAMETER_INFO){
      if (!method.isConstructor()){
        PsiType returnType = method.getReturnType();
        if (substitutor != null) {
          returnType = substitutor.substitute(returnType);
        }

        appendModifierList(buffer, method.getModifierList());
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
    if (numParams > 0){
      for(int j = 0; j < numParams; j++) {
        PsiParameter parm = parms[j];

        int startOffset = buffer.length();

        if (parm.isValid()) {
          PsiType paramType = parm.getType();
          if (substitutor != null) {
            paramType = substitutor.substitute(paramType);
          }
          appendModifierList(buffer, parm.getModifierList());
          buffer.append(paramType.getPresentableText());
          String name = parm.getName();
          if (name != null){
            buffer.append(" ");
            buffer.append(name);
          }
        }

        int endOffset = buffer.length();

        if (j < numParams - 1){
          buffer.append(", ");
        }

        if (context.isUIComponentEnabled() &&
            (j == currentParameter ||
             (j == numParams - 1 && parm.isVarArgs() && currentParameter >= numParams)
            )
           ) {
          highlightStartOffset = startOffset;
          highlightEndOffset = endOffset;
        }
      }
    }
    else{
      buffer.append(CodeInsightBundle.message("parameter.info.no.parameters"));
    }

    if (settings.SHOW_FULL_SIGNATURES_IN_PARAMETER_INFO){
      buffer.append(")");
    }

    context.setupUIComponentPresentation(
      buffer.toString(),
      highlightStartOffset,
      highlightEndOffset,
      !context.isUIComponentEnabled(),
      method.isDeprecated(),
      false,
      context.getDefaultParameterColor()
    );
  }

  private static void appendModifierList(final StringBuilder buffer, final PsiModifierList list) {
    if (list == null) return;
    int lastSize = buffer.length();
    for(PsiAnnotation a: list.getAnnotations()) {
      if (lastSize != buffer.length()) buffer.append(" ");
      final PsiJavaCodeReferenceElement element = a.getNameReferenceElement();
      if (element != null) buffer.append("@").append(element.getText());
    }
    if (lastSize != buffer.length()) buffer.append(" ");
  }

  public void updateUI(final Object p, final ParameterInfoUIContext context) {
    if (p instanceof CandidateInfo) {
      CandidateInfo info = (CandidateInfo) p;
      updateMethodPresentation((PsiMethod)info.getElement(), info.getSubstitutor(), context);
    }
    else updateMethodPresentation((PsiMethod)p,null,context);
  }
}

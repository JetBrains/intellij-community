package com.intellij.codeInsight.hint.api.impls;

import com.intellij.codeInsight.hint.api.*;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Feb 1, 2006
 * Time: 3:16:01 PM
 * To change this template use File | Settings | File Templates.
 */
public class MethodParameterInfoHandler implements ParameterInfoHandler<PsiExpressionList,Object> {
  public Object[] getParametersForLookup(LookupItem item, ParameterInfoContext context) {
    final PsiElement[] allElements = LookupManager.getInstance(context.getProject()).getAllElementsForItem(item);

    if (allElements != null &&
        allElements.length > 0 &&
        allElements[0] instanceof PsiMethod) {
      PsiMethod[] allMethods = new PsiMethod[allElements.length];
      System.arraycopy(allElements, 0, allMethods, 0, allElements.length);
      return allMethods;
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

  public @Nullable PsiExpressionList findElementForParameterInfo(final CreateParameterInfoContext context) {
    final PsiExpressionList argumentList = findArgumentList(context.getFile(), context.getOffset(), context.getParameterListStart());
    if (argumentList != null) {
      CandidateInfo[] candidates = getMethods(argumentList);
      if (candidates.length == 0) {
        DaemonCodeAnalyzer.getInstance(context.getProject()).updateVisibleHighlighters(context.getEditor());
        return null;
      }
      context.setItemsToShow(candidates);
    }
    return argumentList;
  }

  public void showParameterInfo(@NotNull final PsiExpressionList element, final CreateParameterInfoContext context) {
    context.showHint(element, element.getTextRange().getStartOffset(), this);
  }

  public PsiExpressionList findElementForUpdatingParameterInfo(final UpdateParameterInfoContext context) {
    return findArgumentList(context.getFile(), context.getOffset(), context.getParameterStart());
  }

  public void updateParameterInfo(final PsiExpressionList o, final UpdateParameterInfoContext context) {
    updateMethodInfo(o, context);
  }

  public String getParameterCloseChars() {
    return ParameterInfoUtils.DEFAULT_PARAMETER_CLOSE_CHARS;
  }

  public boolean tracksParameterIndex() {
    return true;
  }

  @Nullable
  public static PsiExpressionList findArgumentList(PsiFile file, int offset, int lbraceOffset){
    if (file == null) return null;
    
    char[] chars = file.textToCharArray();
    if (offset >= chars.length) offset = chars.length - 1;
    int offset1 = CharArrayUtil.shiftBackward(chars, offset, " \t\n\r");
    if (offset1 < 0) return null;
    boolean acceptRparenth = true;
    boolean acceptLparenth = false;
    if (offset1 != offset){
      offset = offset1;
      acceptRparenth = false;
      acceptLparenth = true;
    }

    PsiElement element = file.findElementAt(offset);
    if (element == null) return null;
    PsiElement parent = element.getParent();
    while(true){
      if (parent instanceof PsiExpressionList) {
        TextRange range = parent.getTextRange();
        if (!acceptRparenth){
          if (offset == range.getEndOffset() - 1){
            PsiElement[] children = parent.getChildren();
            PsiElement last = children[children.length - 1];
            if (last instanceof PsiJavaToken && ((PsiJavaToken)last).getTokenType() == JavaTokenType.RPARENTH){
              parent = parent.getParent();
              continue;
            }
          }
        }
        if (!acceptLparenth){
          if (offset == range.getStartOffset()){
            parent = parent.getParent();
            continue;
          }
        }
        if (lbraceOffset >= 0 && range.getStartOffset() != lbraceOffset){
          parent = parent.getParent();
          continue;
        }
        break;
      }
      if (parent instanceof PsiFile) return null;
      parent = parent.getParent();
    }
    PsiExpressionList list = (PsiExpressionList)parent;
    PsiElement listParent = list.getParent();
    if (listParent instanceof PsiMethodCallExpression
        || listParent instanceof PsiNewExpression
        || listParent instanceof PsiAnonymousClass
        || listParent instanceof PsiEnumConstant){
      return list;
    }
    else{
      return null;
    }
  }

  private static PsiCall getCall(PsiExpressionList list){
    if (list.getParent() instanceof PsiMethodCallExpression){
      return (PsiCallExpression)list.getParent();
    }
    else if (list.getParent() instanceof PsiNewExpression){
      return (PsiCallExpression)list.getParent();
    }
    else if (list.getParent() instanceof PsiAnonymousClass){
      return (PsiCallExpression)list.getParent().getParent();
    }
    else if (list.getParent() instanceof PsiEnumConstant){
      return (PsiCall)list.getParent();
    }
    else{
      return null;
    }
  }

  public static CandidateInfo[] getMethods(PsiExpressionList argList) {
    final PsiCall call = getCall(argList);
    PsiResolveHelper helper = argList.getManager().getResolveHelper();

    if (call instanceof PsiCallExpression) {
      ArrayList<CandidateInfo> result;
      CandidateInfo[] candidates = helper.getReferencedMethodCandidates((PsiCallExpression)call, true);
      result = new ArrayList<CandidateInfo>();

      if (!(argList.getParent() instanceof PsiAnonymousClass)) {
        for (CandidateInfo candidate : candidates) {
          if (candidate.isStaticsScopeCorrect() && candidate.isAccessible()) result.add(candidate);
        }
      }
      else {
        PsiClass aClass = (PsiAnonymousClass)argList.getParent();
        for (CandidateInfo candidate : candidates) {
          if (candidate.isStaticsScopeCorrect() && helper.isAccessible(((PsiMethod)candidate.getElement()), argList, aClass)) {
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

  public static void updateMethodInfo(PsiExpressionList list, UpdateParameterInfoContext context) {
    int index = ParameterInfoUtils.getCurrentParameterIndex(list.getNode(), context.getOffset(),JavaTokenType.COMMA);
    context.setCurrentParameter(index);

    Object[] candidates = context.getObjectsToView();
    PsiExpression[] args = list.getExpressions();
    for(int i = 0; i < candidates.length; i++) {
      boolean enabled = true;
      CandidateInfo candidate = (CandidateInfo) candidates[i];
      PsiMethod method = (PsiMethod) candidate.getElement();
      PsiSubstitutor substitutor = candidate.getSubstitutor();
      assert substitutor != null;

      if (!method.isValid() || !substitutor.isValid()){ // this may sometimes happen e,g, when editing method call in field initializer candidates in the same file get invalidated
        context.setUIComponentEnabled(i, false);
        continue;
      }

      PsiParameter[] parms = method.getParameterList().getParameters();
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
                for (int j = parms.length; j <= index; j++) {
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
        for(int j = 0; j < index; j++){
          PsiParameter parm = parms[j];
          PsiExpression arg = args[j];
          assert parm.isValid();
          assert arg.isValid();
          PsiType parmType = substitutor.substitute(parm.getType());
          PsiType argType = arg.getType();
          if (argType != null && !parmType.isAssignableFrom(argType)){
            enabled = false;
            break;
          }
        }
      }

      context.setUIComponentEnabled(i, enabled);
    }
  }

  private static void updateMethodPresentation(PsiMethod method, PsiSubstitutor substitutor, ParameterInfoUIContext context) {
    CodeInsightSettings settings = CodeInsightSettings.getInstance();

    if (!method.isValid()){
      context.setUIComponentEnabled(false);
      return;
    }

    int highlightStartOffset = -1;
    int highlightEndOffset = -1;

    StringBuffer buffer = new StringBuffer();

    if (settings.SHOW_FULL_SIGNATURES_IN_PARAMETER_INFO){
      if (!method.isConstructor()){
        PsiType returnType = method.getReturnType();
        if (substitutor != null) {
          returnType = substitutor.substitute((returnType));
        }
        buffer.append(returnType.getPresentableText());
        buffer.append(" ");
      }
      buffer.append(method.getName());
      buffer.append("(");
    }

    final int currentParameter = context.getCurrentParameterIndex();

    PsiParameter[] parms = method.getParameterList().getParameters();
    int numParams = parms.length;
    if (numParams > 0){
      for(int j = 0; j < numParams; j++) {
        PsiParameter parm = parms[j];

        int startOffset = buffer.length();

        if (parm.isValid()) {
          PsiType paramType = parm.getType();
          if (substitutor != null) {
            paramType = substitutor.substitute(paramType);
          }
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

  public void updateUI(final Object p, final ParameterInfoUIContext context) {
    if (p instanceof CandidateInfo) {
      CandidateInfo info = (CandidateInfo) p;
      updateMethodPresentation((PsiMethod)info.getElement(), info.getSubstitutor(), context);
    }
    else updateMethodPresentation((PsiMethod)p,null,context);
  }

}

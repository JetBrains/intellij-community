/*
 * Copyright 2003-2025 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.jdk;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.JavaResolveResult;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiCall;
import com.intellij.psi.PsiCapturedWildcardType;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiEllipsisType;
import com.intellij.psi.PsiEnumConstant;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.source.javadoc.PsiDocMethodOrFieldRef;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.javadoc.PsiInlineDocTag;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

public final class VarargParameterInspection extends BaseInspection {

  @Override
  public @NotNull String getID() {
    return "VariableArgumentMethod";
  }

  @Override
  public @NotNull String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("variable.argument.method.problem.descriptor");
  }

  @Override
  protected @NotNull LocalQuickFix buildFix(Object... infos) {
    return new VarargParameterFix();
  }

  private static class VarargParameterFix extends PsiUpdateModCommandQuickFix {
    @Override
    public @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("variable.argument.method.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      process(updater, (PsiMethod)element.getParent());
    }

    private static void process(@Nullable ModPsiUpdater updater, PsiMethod method) {
      final PsiParameterList parameterList = method.getParameterList();
      int count = parameterList.getParametersCount();
      final PsiParameter lastParameter = parameterList.getParameter(count - 1);
      if (lastParameter == null || !lastParameter.isVarArgs()) {
        return;
      }
      final List<PsiElement> refElements = updater == null ? getReferences(method) : 
                                           ContainerUtil.map(getReferences(method), e -> updater.getWritable(e));
      performModification(method, count - 1, lastParameter, refElements);
    }

    private static @NotNull @Unmodifiable List<PsiElement> getReferences(@NotNull PsiMethod method) {
      if (IntentionPreviewUtils.isIntentionPreviewActive()) {
        return SyntaxTraverser.psiTraverser(method.getContainingFile())
          .filter(ref -> ref instanceof PsiJavaCodeReferenceElement element && element.isReferenceTo(method) ||
                         ref instanceof PsiEnumConstant constant && method.isEquivalentTo(constant.resolveMethod()) ||
                         ref instanceof PsiDocMethodOrFieldRef && ref.getReference() instanceof PsiReference docRef && docRef.isReferenceTo(method))
          .toList();
      }
      return ContainerUtil.map(ReferencesSearch.search(method).findAll(), PsiReference::getElement);
    }

    private static void performModification(@NotNull PsiMethod method,
                                            int indexOfFirstVarargArgument,
                                            @NotNull PsiParameter lastParameter,
                                            @NotNull List<PsiElement> references) {
      for (PsiElement reference : references) {
          if (reference instanceof PsiCall call) modifyCall(lastParameter, indexOfFirstVarargArgument, call);
          else if (reference.getParent() instanceof PsiCall call) modifyCall(lastParameter, indexOfFirstVarargArgument, call);
          modifyJavadoc(indexOfFirstVarargArgument, reference);
      }
      PsiEllipsisType type = (PsiEllipsisType)lastParameter.getType();
      final PsiType arrayType = type.toArrayType();
      final PsiTypeElement newTypeElement = JavaPsiFacade.getElementFactory(lastParameter.getProject()).createTypeElement(arrayType);
      final PsiAnnotation annotation = AnnotationUtil.findAnnotation(method, CommonClassNames.JAVA_LANG_SAFE_VARARGS);
      if (annotation != null) {
        annotation.delete();
      }
      PsiTypeElement typeElement = lastParameter.getTypeElement();
      assert typeElement != null;
      PsiElement result = new CommentTracker().replaceAndRestoreComments(typeElement, newTypeElement);
      JavaCodeStyleManager.getInstance(method.getProject()).shortenClassReferences(result);
    }

    private static void modifyJavadoc(int indexOfFirstVarargArgument, @NotNull PsiElement reference) {
      if (!(reference instanceof PsiDocMethodOrFieldRef ref)) return;
      String[] signature = ref.getSignature();
      if (signature == null || signature.length -1 != indexOfFirstVarargArgument) return;
      PsiElement name = ref.getNameElement();
      if (name == null) return;
      String vararg = signature[indexOfFirstVarargArgument];
      if (!vararg.endsWith("...")) return;
      vararg = vararg.substring(0, vararg.length() - 3) + "[]";

      final StringBuilder text = new StringBuilder();
      text.append("/** {@link #").append(name.getText()).append("(");
      for (int i = 0; i < signature.length -1; i++) {
        text.append(signature[i]).append(",");
      }
      text.append(vararg).append(")} */");
      final Project project = reference.getProject();
      PsiComment comment = JavaPsiFacade.getElementFactory(project).createCommentFromText(text.toString(), reference);

      PsiElement inlineDocTag = ContainerUtil.find(comment.getChildren(), c -> c instanceof PsiInlineDocTag);
      if (inlineDocTag == null) return;
      PsiElement newElement = ContainerUtil.find(inlineDocTag.getChildren(), c -> c instanceof PsiDocMethodOrFieldRef);
      if (newElement == null) return;
      reference.replace(newElement);
    }
  }

  public static void modifyCall(PsiParameter varargParameter, int indexOfFirstVarargArgument, @NotNull PsiCall call) {
    JavaResolveResult resolveResult = call.resolveMethodGenerics();
    if (resolveResult instanceof MethodCandidateInfo info
        && info.getApplicabilityLevel() != MethodCandidateInfo.ApplicabilityLevel.VARARGS) {
      return;
    }
    final PsiExpressionList argumentList = call.getArgumentList();
    if (argumentList == null) {
      return;
    }
    final @NonNls StringBuilder builder = new StringBuilder("new ");
    final PsiSubstitutor substitutor = resolveResult.getSubstitutor();
    final PsiType componentType = ((PsiEllipsisType)varargParameter.getType()).getComponentType();
    PsiType type = substitutor.substitute(componentType);
    if (type instanceof PsiCapturedWildcardType wildcardType) {
      type = wildcardType.getLowerBound();
    }
    builder.append(JavaGenericsUtil.isReifiableType(type)
                   ? type.getCanonicalText()
                   : TypeConversionUtil.erasure(type).getCanonicalText());
    builder.append("[]{");
    final PsiExpression[] arguments = argumentList.getExpressions();
    final PsiElement start;
    final PsiElement end;
    if (arguments.length > indexOfFirstVarargArgument) {
      start = skipToIncludeComments(arguments[indexOfFirstVarargArgument], false);
      end = skipToIncludeComments(arguments[arguments.length - 1], true);
      PsiElement element = start;
      while (true) {
        builder.append(element.getText());
        if (element == end) break;
        element = element.getNextSibling();
      }
      argumentList.deleteChildRange(start, end);
    }
    builder.append('}');
    final Project project = call.getProject();
    final PsiExpression arrayExpression = JavaPsiFacade.getElementFactory(project).createExpressionFromText(builder.toString(), call);
    argumentList.add(arrayExpression);
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(argumentList);
    CodeStyleManager.getInstance(project).reformat(argumentList);
  }
  
  private static PsiElement skipToIncludeComments(PsiElement element, boolean forward) {
    PsiElement sibling = forward ? element.getNextSibling() : element.getPrevSibling();
    if (sibling instanceof PsiComment) return skipToIncludeComments(sibling, forward);
    else if (sibling instanceof PsiWhiteSpace) {
      sibling = forward ? sibling.getNextSibling() : sibling.getPrevSibling();
      if (sibling instanceof PsiComment) return skipToIncludeComments(sibling, forward);
    }
    return element;
  }

  @Override
  public @NotNull BaseInspectionVisitor buildVisitor() {
    return new VarargParameterVisitor();
  }

  private static class VarargParameterVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      if (method.isVarArgs()) {
        if (isVisibleHighlight(method)) {
          registerMethodError(method);
        }
        else {
          registerErrorAtRange(method.getFirstChild(), method.getParameterList());
        }
      }
    }
  }

  /**
   * Silently updates method to make it non-vararg
   * 
   * @param method method to convert
   */
  public static void convertVarargMethodToArray(@NotNull PsiMethod method) {
    VarargParameterFix.process(null, method);
  }
}
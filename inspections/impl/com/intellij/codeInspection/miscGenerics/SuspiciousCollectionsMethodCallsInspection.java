package com.intellij.codeInspection.miscGenerics;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.IntArrayList;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ven
 */
public class SuspiciousCollectionsMethodCallsInspection extends GenericsInspectionToolBase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.miscGenerics.SuspiciousCollectionsMethodCallsInspection");

  private static void setupPatternMethods(PsiManager manager,
                                   GlobalSearchScope searchScope,
                                   List<PsiMethod> patternMethods,
                                   IntArrayList indices) throws IncorrectOperationException {
    final PsiClass collectionClass = manager.findClass("java.util.Collection", searchScope);
    PsiType[] javaLangObject = {PsiType.getJavaLangObject(manager, searchScope)};
    MethodSignature removeSignature = MethodSignatureUtil.createMethodSignature("remove", javaLangObject, PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY);
    if (collectionClass != null) {
      PsiMethod remove = MethodSignatureUtil.findMethodBySignature(collectionClass, removeSignature, false);
      addMethod(remove, 0, patternMethods, indices);
      MethodSignature containsSignature = MethodSignatureUtil.createMethodSignature("contains", javaLangObject, PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY);
      PsiMethod contains = MethodSignatureUtil.findMethodBySignature(collectionClass, containsSignature, false);
      addMethod(contains, 0, patternMethods, indices);
    }

    final PsiClass listClass = manager.findClass("java.util.List", searchScope);
    if (listClass != null) {
      MethodSignature indexofSignature = MethodSignatureUtil.createMethodSignature("indexOf", javaLangObject, PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY);
      PsiMethod indexof = MethodSignatureUtil.findMethodBySignature(listClass, indexofSignature, false);
      addMethod(indexof, 0, patternMethods, indices);
      MethodSignature lastindexofSignature = MethodSignatureUtil.createMethodSignature("lastIndexOf", javaLangObject, PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY);
      PsiMethod lastindexof = MethodSignatureUtil.findMethodBySignature(listClass, lastindexofSignature, false);
      addMethod(lastindexof, 0, patternMethods, indices);
    }

    final PsiClass mapClass = manager.findClass("java.util.Map", searchScope);
    if (mapClass != null) {
      PsiMethod remove = MethodSignatureUtil.findMethodBySignature(mapClass, removeSignature, false);
      addMethod(remove, 0, patternMethods, indices);
      MethodSignature getSignature = MethodSignatureUtil.createMethodSignature("get", javaLangObject, PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY);
      PsiMethod get = MethodSignatureUtil.findMethodBySignature(mapClass, getSignature, false);
      addMethod(get, 0, patternMethods, indices);
      MethodSignature containsKeySignature = MethodSignatureUtil.createMethodSignature("containsKey", javaLangObject, PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY);
      PsiMethod containsKey = MethodSignatureUtil.findMethodBySignature(mapClass, containsKeySignature, false);
      addMethod(containsKey, 0, patternMethods, indices);
      MethodSignature containsValueSignature = MethodSignatureUtil.createMethodSignature("containsValue", javaLangObject, PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY);
      PsiMethod containsValue = MethodSignatureUtil.findMethodBySignature(mapClass, containsValueSignature, false);
      addMethod(containsValue, 1, patternMethods, indices);
    }

    patternMethods.remove(null);
  }

  private static void addMethod(final PsiMethod patternMethod, int typeParamIndex, List<PsiMethod> patternMethods, IntArrayList indices) {
    if (patternMethod != null) {
      patternMethods.add(patternMethod);
      indices.add(typeParamIndex);
    }
  }

  public ProblemDescriptor[] getDescriptions(PsiElement place, final InspectionManager manager) {
    final List<ProblemDescriptor> problems = new ArrayList<ProblemDescriptor>();
    final List<PsiMethod> patternMethods = new ArrayList<PsiMethod>();
    final IntArrayList indices = new IntArrayList();
    try {
      setupPatternMethods(place.getManager(), place.getResolveScope(), patternMethods, indices);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
      return null;
    }

    place.accept(new PsiRecursiveElementVisitor() {
      public void visitMethodCallExpression(PsiMethodCallExpression methodCall) {
        super.visitMethodCallExpression(methodCall);
        final PsiReferenceExpression methodExpression = methodCall.getMethodExpression();
        final PsiExpression qualifier = methodExpression.getQualifierExpression();
        if (qualifier == null || qualifier instanceof PsiThisExpression || qualifier instanceof PsiSuperExpression) return;
        final PsiExpression[] args = methodCall.getArgumentList().getExpressions();
        if (args.length != 1) return;
        PsiType argType = args[0].getType();
        if (argType instanceof PsiPrimitiveType) {
          argType = ((PsiPrimitiveType)argType).getBoxedType(methodCall);
        }

        if (!(argType instanceof PsiClassType)) return;

        final JavaResolveResult resolveResult = methodExpression.advancedResolve(false);
        final PsiMethod calleeMethod = (PsiMethod)resolveResult.getElement();
        if (calleeMethod == null) return;
        PsiMethod contextMethod = PsiTreeUtil.getParentOfType(methodCall, PsiMethod.class);

        for (int i = 0; i < patternMethods.size(); i++) {
          PsiMethod patternMethod = patternMethods.get(i);
          int index = indices.get(i);
          if (!patternMethod.getName().equals(methodExpression.getReferenceName())) continue;

          //we are in collections method implementation
          if (contextMethod != null && isInheritorOrSelf(contextMethod, patternMethod)) return;

          if (isInheritorOrSelf(calleeMethod, patternMethod)) {
            PsiTypeParameter[] typeParameters = calleeMethod.getContainingClass().getTypeParameters();
            if (typeParameters.length <= index) return;
            final PsiTypeParameter typeParameter = typeParameters[index];
            PsiType typeParamMapping = resolveResult.getSubstitutor().substitute(typeParameter);
            if (typeParamMapping != null) {
              String message = null;
              if (!typeParamMapping.isAssignableFrom(argType)) {
                if (!typeParamMapping.isConvertibleFrom(argType)) {
                  PsiType qualifierType = qualifier.getType();
                  LOG.assertTrue(qualifierType != null);

                  message = InspectionsBundle.message("inspection.suspicious.collections.method.calls.problem.descriptor",
                      PsiFormatUtil.formatType(qualifierType, 0, PsiSubstitutor.EMPTY),
                      PsiFormatUtil.formatType(argType, 0, PsiSubstitutor.EMPTY)
                      );
                } else {
                  message = InspectionsBundle.message("inspection.suspicious.collections.method.calls.problem.descriptor1",
                      PsiFormatUtil.formatMethod(calleeMethod, resolveResult.getSubstitutor(),
                          PsiFormatUtil.SHOW_NAME |
                              PsiFormatUtil.SHOW_CONTAINING_CLASS,
                          PsiFormatUtil.SHOW_TYPE));
                }
              }
              if (message != null) {
                problems.add(manager.createProblemDescriptor(args[0], message,
                    (LocalQuickFix []) null,
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
              }
            }
            return;
          }
        }
      }

      private boolean isInheritorOrSelf(PsiMethod inheritorCandidate, PsiMethod base) {
        PsiClass aClass = inheritorCandidate.getContainingClass();
        PsiClass bClass = base.getContainingClass();
        if (aClass == null || bClass == null) return false;
        PsiSubstitutor substitutor = TypeConversionUtil.getClassSubstitutor(bClass, aClass, PsiSubstitutor.EMPTY);
        if (substitutor == null) return false;
        return MethodSignatureUtil.findMethodBySignature(bClass, inheritorCandidate.getSignature(substitutor), false) == base;
      }
    });

    if (problems.isEmpty()) return null;
    return problems.toArray(new ProblemDescriptor[problems.size()]);
  }

  public String getDisplayName() {
    return InspectionsBundle.message("inspection.suspicious.collections.method.calls.display.name");
  }

  public String getGroupDisplayName() {
    return GroupNames.BUGS_GROUP_NAME;
  }

  public String getShortName() {
    return "SuspiciousMethodCalls";
  }
}

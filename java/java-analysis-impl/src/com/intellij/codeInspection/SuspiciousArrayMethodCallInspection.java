// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.psi.*;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class SuspiciousArrayMethodCallInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final Set<String> INTERESTING_NAMES = ContainerUtil.set("fill", "binarySearch", "equals", "mismatch");

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression call) {
        PsiElement nameElement = call.getMethodExpression().getReferenceNameElement();
        if (nameElement == null) return;
        String name = nameElement.getText();
        if (!INTERESTING_NAMES.contains(name)) return;
        PsiMethod method = call.resolveMethod();
        if (method == null) return;
        PsiClass aClass = method.getContainingClass();
        if (aClass == null || !CommonClassNames.JAVA_UTIL_ARRAYS.equals(aClass.getQualifiedName())) return;
        PsiExpression[] args = call.getArgumentList().getExpressions();
        switch (name) {
          case "fill":
          case "binarySearch":
            if (args.length == 2) {
              handleArrayElement(args[0], args[1]);
            }
            else if (args.length == 4) {
              handleArrayElement(args[0], args[3]);
            }
            break;
          case "equals":
          case "mismatch":
            if (args.length == 2) {
              handleArrays(nameElement, args[0], args[1]);
            }
            else if (args.length == 6) {
              handleArrays(nameElement, args[0], args[3]);
            }
            break;
        }
      }

      private void handleArrayElement(PsiExpression array, PsiExpression element) {
        PsiType arrayType = array.getType();
        PsiType elementType = element.getType();
        if (elementType == null || !(arrayType instanceof PsiArrayType)) return;
        PsiType arrayElementType = ((PsiArrayType)arrayType).getComponentType();
        // incompatible primitive array will be reported anyways as compilation error
        if (arrayElementType instanceof PsiPrimitiveType) return;
        elementType = TypeConversionUtil.erasure(elementType);
        arrayElementType = TypeConversionUtil.erasure(arrayElementType);
        if (!TypeConversionUtil.areTypesConvertible(elementType, arrayElementType)) {
          holder.registerProblem(element, InspectionsBundle.message("inspection.suspicious.array.method.call.problem.element"));
        }
      }

      private void handleArrays(PsiElement context, PsiExpression array1, PsiExpression array2) {
        PsiType array1Type = array1.getType();
        PsiType array2Type = array2.getType();
        if (!(array1Type instanceof PsiArrayType) || !(array2Type instanceof PsiArrayType)) return;
        PsiType array1ElementType = ((PsiArrayType)array1Type).getComponentType();
        PsiType array2ElementType = ((PsiArrayType)array2Type).getComponentType();
        // incompatible primitive array will be reported anyways as compilation error
        if (array1ElementType instanceof PsiPrimitiveType || array2ElementType instanceof PsiPrimitiveType) return;
        array1ElementType = TypeConversionUtil.erasure(array1ElementType);
        array2ElementType = TypeConversionUtil.erasure(array2ElementType);
        if (!TypeConversionUtil.areTypesConvertible(array1ElementType, array2ElementType) ||
            !TypeConversionUtil.areTypesConvertible(array2ElementType, array1ElementType)) {
          holder.registerProblem(context, InspectionsBundle.message("inspection.suspicious.array.method.call.problem.arrays"));
        }
      }
    };
  }
}

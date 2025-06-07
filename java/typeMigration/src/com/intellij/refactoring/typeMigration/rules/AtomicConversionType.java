// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.typeMigration.rules;

import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.impl.JavaConstantExpressionEvaluator;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.concurrent.atomic.*;

enum AtomicConversionType {
  ATOMIC_INTEGER {
    @Override
    protected boolean accept(PsiType from, PsiClassType to, PsiExpression context) {
      return PsiTypes.intType().isAssignableFrom(from) && to.getCanonicalText().equals(AtomicInteger.class.getName());
    }

    @Override
    protected boolean checkDefaultValue(PsiExpression expr) {
      return Objects.equals(JavaConstantExpressionEvaluator.computeConstantExpression(expr, false), 0);
    }
  },
  ATOMIC_LONG {
    @Override
    protected boolean accept(PsiType from, PsiClassType to, PsiExpression context) {
      return PsiTypes.longType().isAssignableFrom(from) && to.getCanonicalText().equals(AtomicLong.class.getName());
    }

    @Override
    protected boolean checkDefaultValue(PsiExpression expr) {
      return Objects.equals(JavaConstantExpressionEvaluator.computeConstantExpression(expr, false), 0);
    }
  },
  ATOMIC_BOOLEAN {
    @Override
    protected boolean accept(PsiType from, PsiClassType to, PsiExpression context) {
      return PsiTypes.booleanType().equals(from) && to.getCanonicalText().equals(AtomicBoolean.class.getName());
    }

    @Override
    protected boolean checkDefaultValue(PsiExpression expr) {
      return false;
    }
  },
  ATOMIC_REFERENCE_OR_ARRAY {
    @Override
    protected boolean accept(PsiType from, PsiClassType to, PsiExpression context) {
      if (from.equals(PsiTypes.intType().createArrayType()) && to.getCanonicalText().equals(AtomicIntegerArray.class.getName())) {
        return true;
      }
      if (from.equals(PsiTypes.longType().createArrayType()) && to.getCanonicalText().equals(AtomicLongArray.class.getName())) {
        return true;
      }
      final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(to);
      final PsiClass atomicClass = resolveResult.getElement();

      if (atomicClass != null) {
        final String typeQualifiedName = atomicClass.getQualifiedName();
        if (!Comparing.strEqual(typeQualifiedName, AtomicReference.class.getName()) &&
            !Comparing.strEqual(typeQualifiedName, AtomicReferenceArray.class.getName())) {
          return false;
        }
        final PsiTypeParameter[] typeParameters = atomicClass.getTypeParameters();
        if (typeParameters.length != 1) return false;
        final PsiType toTypeParameterValue = resolveResult.getSubstitutor().substitute(typeParameters[0]);
        if (from instanceof PsiArrayType arrayType) {
          from = arrayType.getComponentType();
        }
        if (toTypeParameterValue != null) {
          if (from instanceof PsiPrimitiveType) {
            final PsiPrimitiveType unboxedInitialType = PsiPrimitiveType.getUnboxedType(toTypeParameterValue);
            if (unboxedInitialType != null) {
              return TypeConversionUtil.areTypesConvertible(unboxedInitialType, from);
            }
          }
          else {
            return TypeConversionUtil.isAssignable(PsiUtil.captureToplevelWildcards(toTypeParameterValue, context), from);
          }
        }
      }
      return false;
    }

    @Override
    protected boolean checkDefaultValue(PsiExpression expr) {
      return PsiTypes.nullType().equals(expr.getType());
    }
  };

  protected abstract boolean accept(PsiType from, PsiClassType to, PsiExpression context);

  protected abstract boolean checkDefaultValue(PsiExpression expr);

  static @Nullable AtomicConversionType getConversionType(PsiType from, PsiClassType to, PsiExpression context) {
    return ContainerUtil.find(values(), type -> type.accept(from, to, context));
  }
}

/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.psi.util;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class PsiTypesUtil {
  @NonNls private static final Map<String, String> ourUnboxedTypes = new THashMap<String, String>();
  @NonNls private static final Map<String, String> ourBoxedTypes = new THashMap<String, String>();

  static {
    ourUnboxedTypes.put(CommonClassNames.JAVA_LANG_BOOLEAN, "boolean");
    ourUnboxedTypes.put(CommonClassNames.JAVA_LANG_BYTE, "byte");
    ourUnboxedTypes.put(CommonClassNames.JAVA_LANG_SHORT, "short");
    ourUnboxedTypes.put(CommonClassNames.JAVA_LANG_INTEGER, "int");
    ourUnboxedTypes.put(CommonClassNames.JAVA_LANG_LONG, "long");
    ourUnboxedTypes.put(CommonClassNames.JAVA_LANG_FLOAT, "float");
    ourUnboxedTypes.put(CommonClassNames.JAVA_LANG_DOUBLE, "double");
    ourUnboxedTypes.put(CommonClassNames.JAVA_LANG_CHARACTER, "char");

    ourBoxedTypes.put("boolean", CommonClassNames.JAVA_LANG_BOOLEAN);
    ourBoxedTypes.put("byte", CommonClassNames.JAVA_LANG_BYTE);
    ourBoxedTypes.put("short", CommonClassNames.JAVA_LANG_SHORT);
    ourBoxedTypes.put("int", CommonClassNames.JAVA_LANG_INTEGER);
    ourBoxedTypes.put("long", CommonClassNames.JAVA_LANG_LONG);
    ourBoxedTypes.put("float", CommonClassNames.JAVA_LANG_FLOAT);
    ourBoxedTypes.put("double", CommonClassNames.JAVA_LANG_DOUBLE);
    ourBoxedTypes.put("char", CommonClassNames.JAVA_LANG_CHARACTER);
  }

  @NonNls private static final String GET_CLASS_METHOD = "getClass";

  private PsiTypesUtil() { }

  @NotNull
  public static String getDefaultValueOfType(PsiType type) {
    if (type instanceof PsiArrayType) {
      int count = type.getArrayDimensions() - 1;
      PsiType componentType = type.getDeepComponentType();

      if (componentType instanceof PsiClassType) {
        final PsiClassType classType = (PsiClassType)componentType;
        if (classType.resolve() instanceof PsiTypeParameter) {
          return PsiKeyword.NULL;
        }
      }

      StringBuilder buffer = new StringBuilder();
      buffer.append(PsiKeyword.NEW);
      buffer.append(" ");
      buffer.append(componentType.getCanonicalText());
      buffer.append("[0]");
      for (int i = 0; i < count; i++) {
        buffer.append("[]");
      }
      return buffer.toString();
    }
    if (type instanceof PsiPrimitiveType) {
      return PsiType.BOOLEAN.equals(type) ? PsiKeyword.FALSE : "0";
    }
    return PsiKeyword.NULL;
  }

  /**
   * Returns the unboxed type name or parameter.
   * @param type boxed java type name
   * @return unboxed type name if available; same value otherwise
   */
  @Nullable
  public static String unboxIfPossible(final String type) {
    if (type == null) return null;
    final String s = ourUnboxedTypes.get(type);
    return s == null? type : s;
  }

  /**
   * Returns the boxed type name or parameter.
   * @param type primitive java type name
   * @return boxed type name if available; same value otherwise
   */
  @Nullable
  public static String boxIfPossible(final String type) {
    if (type == null) return null;
    final String s = ourBoxedTypes.get(type);
    return s == null ? type : s;
  }

  @Nullable
  public static PsiClass getPsiClass(@Nullable PsiType psiType) {
    return psiType instanceof PsiClassType? ((PsiClassType)psiType).resolve() : null;
  }

  public static PsiClassType getClassType(@NotNull PsiClass psiClass) {
    return JavaPsiFacade.getElementFactory(psiClass.getProject()).createType(psiClass);
  }

  @Nullable
  public static PsiClassType getLowestUpperBoundClassType(@NotNull final PsiDisjunctionType type) {
    final PsiType lub = type.getLeastUpperBound();
    if (lub instanceof PsiClassType) {
      return (PsiClassType)lub;
    }
    else if (lub instanceof PsiIntersectionType) {
      for (PsiType subType : ((PsiIntersectionType)lub).getConjuncts()) {
        if (subType instanceof PsiClassType) {
          final PsiClass aClass = ((PsiClassType)subType).resolve();
          if (aClass != null && !aClass.isInterface()) {
            return (PsiClassType)subType;
          }
        }
      }
    }
    return null;
  }

  public static PsiType patchMethodGetClassReturnType(@NotNull PsiMethodReferenceExpression methodExpression,
                                                      @NotNull PsiMethod method) {
    if (isGetClass(method)) {
      final PsiType qualifierType = PsiMethodReferenceUtil.getQualifierType(methodExpression);
      return qualifierType != null ? createJavaLangClassType(methodExpression, qualifierType, true) : null;
    }
    return null;
  }
  
  public static PsiType patchMethodGetClassReturnType(@NotNull PsiExpression call,
                                                      @NotNull PsiReferenceExpression methodExpression,
                                                      @NotNull PsiMethod method,
                                                      @Nullable Condition<IElementType> condition,
                                                      @NotNull LanguageLevel languageLevel) {
    //JLS3 15.8.2
    if (languageLevel.isAtLeast(LanguageLevel.JDK_1_5) && isGetClass(method)) {
      PsiExpression qualifier = methodExpression.getQualifierExpression();
      PsiType qualifierType = null;
      final Project project = call.getProject();
      if (qualifier != null) {
        qualifierType = TypeConversionUtil.erasure(qualifier.getType());
      }
      else if (condition != null) {
        ASTNode parent = call.getNode().getTreeParent();
        while (parent != null && condition.value(parent.getElementType())) {
          parent = parent.getTreeParent();
        }
        if (parent != null) {
          qualifierType = JavaPsiFacade.getInstance(project).getElementFactory().createType((PsiClass)parent.getPsi());
        }
      }
      return createJavaLangClassType(methodExpression, qualifierType, true);
    }
    return null;
  }

  public static boolean isGetClass(PsiMethod method) {
    return GET_CLASS_METHOD.equals(method.getName()) && CommonClassNames.JAVA_LANG_OBJECT.equals(method.getContainingClass().getQualifiedName());
  }

  @Nullable
  public static PsiType createJavaLangClassType(@NotNull PsiElement context, @Nullable PsiType qualifierType, boolean captureTopLevelWildcards) {
    if (qualifierType != null) {
      PsiUtil.ensureValidType(qualifierType);
      JavaPsiFacade facade = JavaPsiFacade.getInstance(context.getProject());
      PsiClass javaLangClass = facade.findClass(CommonClassNames.JAVA_LANG_CLASS, context.getResolveScope());
      if (javaLangClass != null && javaLangClass.getTypeParameters().length == 1) {
        PsiSubstitutor substitutor = PsiSubstitutor.EMPTY.
          put(javaLangClass.getTypeParameters()[0], PsiWildcardType.createExtends(context.getManager(), qualifierType));
        final PsiClassType classType = facade.getElementFactory().createType(javaLangClass, substitutor, PsiUtil.getLanguageLevel(context));
        return captureTopLevelWildcards ? PsiUtil.captureToplevelWildcards(classType, context) : classType;
      }
    }
    return null;
  }

  @Nullable
  public static PsiType getExpectedTypeByParent(PsiElement element) {
    final PsiElement parent = PsiUtil.skipParenthesizedExprUp(element.getParent());
    if (parent instanceof PsiVariable) {
      if (PsiUtil.checkSameExpression(element, ((PsiVariable)parent).getInitializer())) {
        return ((PsiVariable)parent).getType();
      }
    }
    else if (parent instanceof PsiAssignmentExpression) {
      if (PsiUtil.checkSameExpression(element, ((PsiAssignmentExpression)parent).getRExpression())) {
        return ((PsiAssignmentExpression)parent).getLExpression().getType();
      }
    }
    else if (parent instanceof PsiReturnStatement) {
      final PsiElement psiElement = PsiTreeUtil.getParentOfType(parent, PsiLambdaExpression.class, PsiMethod.class);
      if (psiElement instanceof PsiLambdaExpression) {
        return null;
      }
      else if (psiElement instanceof PsiMethod){
        return ((PsiMethod)psiElement).getReturnType();
      }
    }
    else if (PsiUtil.isCondition(element, parent)) {
      return PsiType.BOOLEAN.getBoxedType(parent);
    } 
    else if (parent instanceof PsiArrayInitializerExpression) {
      final PsiElement gParent = parent.getParent();
      if (gParent instanceof PsiNewExpression) {
        final PsiType type = ((PsiNewExpression)gParent).getType();
        if (type instanceof PsiArrayType) {
          return ((PsiArrayType)type).getComponentType();
        }
      }
      else if (gParent instanceof PsiVariable) {
        final PsiType type = ((PsiVariable)gParent).getType();
        if (type instanceof PsiArrayType) {
          return ((PsiArrayType)type).getComponentType();
        }
      }
      else if (gParent instanceof PsiArrayInitializerExpression) {
        final PsiType expectedTypeByParent = getExpectedTypeByParent((PsiExpression)parent);
        return expectedTypeByParent != null && expectedTypeByParent instanceof PsiArrayType
               ? ((PsiArrayType)expectedTypeByParent).getComponentType() : null;
      }
    }
    return null;
  }
  
  public static boolean compareTypes(PsiType leftType, PsiType rightType, boolean ignoreEllipsis) {
    if (ignoreEllipsis) {
      if (leftType instanceof PsiEllipsisType) {
        leftType = ((PsiEllipsisType)leftType).toArrayType();
      }
      if (rightType instanceof PsiEllipsisType) {
        rightType = ((PsiEllipsisType)rightType).toArrayType();
      }
    }
    return Comparing.equal(leftType, rightType);
  }

  public static boolean isDenotableType(PsiType type) {
    if (type instanceof PsiWildcardType || type instanceof PsiCapturedWildcardType) {
      return false;
    }
    return true;
  }
  
  public static boolean hasUnresolvedComponents(@NotNull PsiType type) {
    return type.accept(new PsiTypeVisitor<Boolean>() {
      @Nullable
      @Override
      public Boolean visitClassType(PsiClassType classType) {
        final PsiClass psiClass = classType.resolve();
        if (psiClass == null) {
          return true;
        }
        for (PsiType param : classType.getParameters()) {
          if (param.accept(this)) {
            return true;
          }
        }
        return super.visitClassType(classType);
      }

      @Nullable
      @Override
      public Boolean visitArrayType(PsiArrayType arrayType) {
        return arrayType.getComponentType().accept(this);
      }

      @Nullable
      @Override
      public Boolean visitWildcardType(PsiWildcardType wildcardType) {
        final PsiType bound = wildcardType.getBound();
        return bound != null && bound.accept(this);
      }

      @Override
      public Boolean visitType(PsiType type) {
        return false;
      }
    });
  }
}

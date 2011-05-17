/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.psi;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Function;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * User: anna
 * Date: Jul 30, 2010
 */
public class PsiDiamondType extends PsiType {
  private PsiManager myManager;
  private final PsiTypeElement myTypeElement;
  private static final Logger LOG = Logger.getInstance("#" + PsiDiamondType.class.getName());

  public PsiDiamondType(PsiManager manager, PsiTypeElement psiTypeElement) {
    super(PsiAnnotation.EMPTY_ARRAY);
    myManager = manager;
    myTypeElement = psiTypeElement;
  }

  @Override
  public String getPresentableText() {
    return "";
  }

  @Override
  public String getCanonicalText() {
    return "";
  }

  @Override
  public String getInternalCanonicalText() {
    return "Diamond Type";
  }

  @Override
  public boolean isValid() {
    return false;
  }

  @Override
  public boolean equalsToText(@NonNls String text) {
    return text != null && text.isEmpty();
  }

  @Override
  public <A> A accept(PsiTypeVisitor<A> visitor) {
    return visitor.visitType(this);
  }

  @Override
  public GlobalSearchScope getResolveScope() {
    return GlobalSearchScope.allScope(myManager.getProject());
  }

  @NotNull
  @Override
  public PsiType[] getSuperTypes() {
    return new PsiType[]{getJavaLangObject(myManager, getResolveScope())};
  }

  public DiamondInferenceResult resolveInferredTypes() {
    final PsiNewExpression newExpression = PsiTreeUtil.getParentOfType(myTypeElement, PsiNewExpression.class);
    if (newExpression == null) {
      return PsiDiamondType.DiamondInferenceResult.NULL_RESULT;
    }

    final PsiAnonymousClass anonymousClass = newExpression.getAnonymousClass();
    if (anonymousClass != null) {
      final PsiElement resolve = anonymousClass.getBaseClassReference().resolve();
      if (resolve instanceof PsiClass && ((PsiClass)resolve).getContainingClass() != null) {
        return PsiDiamondType.DiamondInferenceResult.ANONYMOUS_INNER_RESULT;
      }

    }
    return resolveInferredTypes(newExpression);
  }

  public static DiamondInferenceResult resolveInferredTypes(PsiNewExpression newExpression) {
    return resolveInferredTypes(newExpression, newExpression);
  }

  public static DiamondInferenceResult resolveInferredTypes(PsiNewExpression newExpression,
                                                            PsiElement context) {
    final PsiReferenceParameterList referenceParameterList = PsiTreeUtil.findChildOfType(newExpression, PsiReferenceParameterList.class);
    if (referenceParameterList != null && referenceParameterList.getTypeParameterElements().length > 0) {
      return DiamondInferenceResult.EXPLICIT_CONSTRUCTOR_TYPE_ARGS;
    }

    final PsiClass psiClass = findClass(newExpression);
    if (psiClass == null) return DiamondInferenceResult.NULL_RESULT;
    final PsiExpressionList argumentList = newExpression.getArgumentList();
    if (argumentList == null) return DiamondInferenceResult.NULL_RESULT;
    final PsiMethod constructor = findConstructor(psiClass, newExpression);
    PsiTypeParameter[] params = getAllTypeParams(constructor, psiClass);
    PsiMethod staticFactory = generateStaticFactory(constructor, psiClass, params);
    if (staticFactory == null) {
      return DiamondInferenceResult.NULL_RESULT;
    }
    final PsiSubstitutor inferredSubstitutor = inferTypeParametersForStaticFactory(staticFactory, newExpression, context);
    final PsiTypeParameter[] parameters = staticFactory.getTypeParameters();
    final PsiTypeParameter[] classParameters = psiClass.getTypeParameters();
    final PsiJavaCodeReferenceElement classOrAnonymousClassReference = newExpression.getClassOrAnonymousClassReference();
    LOG.assertTrue(classOrAnonymousClassReference != null);
    final DiamondInferenceResult result = new DiamondInferenceResult(classOrAnonymousClassReference.getReferenceName() + "<>", newExpression.getProject());
    for (PsiTypeParameter parameter : parameters) {
      for (PsiTypeParameter classParameter : classParameters) {
        if (Comparing.strEqual(classParameter.getName(), parameter.getName())) {
          result.addInferredType(inferredSubstitutor.substitute(parameter));
          break;
        }
      }
    }
    return result;
  }


  @Nullable
  private static PsiMethod findConstructor(PsiClass containingClass, PsiNewExpression newExpression) {
    final PsiExpressionList argumentList = newExpression.getArgumentList();
    final Project project = newExpression.getProject();
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    final PsiResolveHelper resolveHelper = facade.getResolveHelper();
    final JavaResolveResult result =
      resolveHelper.resolveConstructor(facade.getElementFactory().createType(containingClass), argumentList, argumentList);
    return (PsiMethod)result.getElement();
  }

  @Nullable
  private static PsiClass findClass(PsiNewExpression newExpression) {
    final PsiJavaCodeReferenceElement classReference = newExpression.getClassOrAnonymousClassReference();
    if (classReference != null) {
      final String text = classReference.getReferenceName();
      if (text != null) {
        final Project project = newExpression.getProject();
        final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
        final PsiResolveHelper resolveHelper = facade.getResolveHelper();
        final PsiElement qualifierElement = classReference.getQualifier();
        final String qualifier = qualifierElement != null ? qualifierElement.getText() : "";
        return resolveHelper.resolveReferencedClass(StringUtil.getQualifiedName(qualifier, text), newExpression);
      } else {
        return null;
      }
    }
    return null;
  }

  @Nullable
  private static PsiMethod generateStaticFactory(@Nullable PsiMethod constructor, PsiClass containingClass, PsiTypeParameter[] params) {
    final StringBuilder buf = new StringBuilder();
    buf.append("public static ");
    buf.append("<");
    buf.append(StringUtil.join(params, new Function<PsiTypeParameter, String>() {
      @Override
      public String fun(PsiTypeParameter psiTypeParameter) {
        return psiTypeParameter.getName();
      }
    }, ", "));
    buf.append(">");

    final String qualifiedName = containingClass.getQualifiedName();
    buf.append(qualifiedName != null ? qualifiedName : containingClass.getName());
    final PsiTypeParameter[] parameters = containingClass.getTypeParameters();
    buf.append("<");
    buf.append(StringUtil.join(parameters, new Function<PsiTypeParameter, String>() {
      @Override
      public String fun(PsiTypeParameter psiTypeParameter) {
        return psiTypeParameter.getName();
      }
    }, ", "));
    buf.append("> ");

    String staticFactoryName = "staticFactory";
    final JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(containingClass.getProject());
    staticFactoryName = styleManager.suggestUniqueVariableName(staticFactoryName, containingClass, false);
    buf.append(staticFactoryName);
    if (constructor == null) {
      buf.append("()");
    }
    else {
      buf.append("(").append(StringUtil.join(constructor.getParameterList().getParameters(), new Function<PsiParameter, String>() {
        int myIdx = 0;
        @Override
        public String fun(PsiParameter psiParameter) {
          return psiParameter.getType().getCanonicalText() + " p" + myIdx++;
        }
      }, ",")).append(")");
    }
    buf.append("{}");

    return JavaPsiFacade.getElementFactory(containingClass.getProject()).createMethodFromText(buf.toString(), constructor != null ? constructor : containingClass);
  }

  private static PsiTypeParameter[] getAllTypeParams(PsiTypeParameterListOwner listOwner, PsiClass containingClass) {
    Set<PsiTypeParameter> params = new LinkedHashSet<PsiTypeParameter>();
    if (listOwner != null) {
      Collections.addAll(params, listOwner.getTypeParameters());
    }
    Collections.addAll(params, containingClass.getTypeParameters());
    return params.toArray(new PsiTypeParameter[params.size()]);
  }


  private static PsiSubstitutor inferTypeParametersForStaticFactory(@NotNull PsiMethod staticFactoryMethod,
                                                                    PsiNewExpression expression,
                                                                    PsiElement parent) {
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(staticFactoryMethod.getProject());
    final PsiResolveHelper resolveHelper = facade.getResolveHelper();
    final PsiParameter[] parameters = staticFactoryMethod.getParameterList().getParameters();
    final PsiExpressionList argumentList = expression.getArgumentList();
    final PsiExpression[] expressions = argumentList.getExpressions();
    return resolveHelper
      .inferTypeArguments(staticFactoryMethod.getTypeParameters(), parameters, expressions, PsiSubstitutor.EMPTY, parent, false);
  }

  public static class DiamondInferenceResult {
    public static final DiamondInferenceResult EXPLICIT_CONSTRUCTOR_TYPE_ARGS = new DiamondInferenceResult() {
      @Override
      public PsiType[] getTypes() {
        return PsiType.EMPTY_ARRAY;
      }

      @Override
      public String getErrorMessage() {
        return "Cannot use diamonds with explicit type parameters for constructor";
      }
    };

    public static final DiamondInferenceResult NULL_RESULT = new DiamondInferenceResult() {
      @Override
      public PsiType[] getTypes() {
        return PsiType.EMPTY_ARRAY;
      }

      @Override
      public String getErrorMessage() {
        return "Cannot infer arguments";
      }
    };

    public static final DiamondInferenceResult ANONYMOUS_INNER_RESULT = new DiamondInferenceResult() {
      @Override
      public PsiType[] getTypes() {
        return PsiType.EMPTY_ARRAY;
      }

      @Override
      public String getErrorMessage() {
        return "Cannot use ''<>'' with anonymous inner classes";
      }
    };

    private List<PsiType> myInferredTypes = new ArrayList<PsiType>();
    private String myErrorMessage;

    private String myNewExpressionPresentableText;
    private Project myProject;

    public DiamondInferenceResult() {
    }

    public DiamondInferenceResult(String expressionPresentableText, Project project) {
      myNewExpressionPresentableText = expressionPresentableText;
      myProject = project;
    }

    public PsiType[] getTypes() {
      if (myErrorMessage != null) {
        return PsiType.EMPTY_ARRAY;
      }
      final PsiType[] result = new PsiType[myInferredTypes.size()];
      for (int i = 0, myInferredTypesSize = myInferredTypes.size(); i < myInferredTypesSize; i++) {
        PsiType inferredType = myInferredTypes.get(i);
        if (inferredType instanceof PsiWildcardType) {
          final PsiType bound = ((PsiWildcardType)inferredType).getBound();
          result[i] = bound != null ? bound : PsiType.getJavaLangObject(PsiManager.getInstance(myProject), GlobalSearchScope.allScope(myProject));
        }
        else {
          result[i] = inferredType;
        }
      }
      return result;
    }

    /**
     * @return all inferred types even if inference failed
     */
    public List<PsiType> getInferredTypes() {
      return myInferredTypes;
    }

    public String getErrorMessage() {
      return myErrorMessage;
    }

    public void addInferredType(PsiType psiType) {
      if (myErrorMessage != null) return;
      if (psiType == null) {
        myErrorMessage = "Cannot infer type arguments for " + myNewExpressionPresentableText;
      } /*else if (!isValid(psiType)) {
        myErrorMessage = "Cannot infer type arguments for " +
                         myNewExpressionPresentableText + " because type " + psiType.getPresentableText() + " inferred is not allowed in current context";
      }*/ else {
        myInferredTypes.add(psiType);
      }
    }

    private static Boolean isValid(PsiType type) {
      return type.accept(new PsiTypeVisitor<Boolean>() {
        @Override
        public Boolean visitType(PsiType type) {
          return !(type instanceof PsiIntersectionType);
        }

        @Override
        public Boolean visitCapturedWildcardType(PsiCapturedWildcardType capturedWildcardType) {
          return false;
        }

        @Override
        public Boolean visitWildcardType(PsiWildcardType wildcardType) {
          final PsiType bound = wildcardType.getBound();
          if (bound != null) {
            if (bound instanceof PsiIntersectionType) return false;
            return bound.accept(this);
          }
          return true;
        }

        @Override
        public Boolean visitClassType(PsiClassType classType) {
          for (PsiType psiType : classType.getParameters()) {
            if (!psiType.accept(this)) {
              return false;
            }
          }
          return true;
        }
      });
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      DiamondInferenceResult that = (DiamondInferenceResult)o;

      if (myErrorMessage != null ? !myErrorMessage.equals(that.myErrorMessage) : that.myErrorMessage != null) return false;
      if (!myInferredTypes.equals(that.myInferredTypes)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = myInferredTypes.hashCode();
      result = 31 * result + (myErrorMessage != null ? myErrorMessage.hashCode() : 0);
      return result;
    }
  }

  public static boolean hasDefaultConstructor(@NotNull final PsiClass psiClass) {
    final PsiMethod[] constructors = psiClass.getConstructors();
    for (PsiMethod method : constructors) {
      if (method.getParameterList().getParametersCount() == 0) return true;
    }
    return constructors.length == 0;
  }

  public static boolean haveConstructorsGenericsParameters(@NotNull final PsiClass psiClass) {
    for (PsiMethod method : psiClass.getConstructors()) {
      for (PsiParameter parameter : method.getParameterList().getParameters()) {
        final PsiType type = parameter.getType();
        final Boolean accept = type.accept(new PsiTypeVisitor<Boolean>() {
          @Override
          public Boolean visitArrayType(PsiArrayType arrayType) {
            return arrayType.getComponentType().accept(this);
          }

          @Override
          public Boolean visitClassType(PsiClassType classType) {
            for (PsiType psiType : classType.getParameters()) {
              if (psiType != null) {
                final Boolean typaParamFound = psiType.accept(this);
                if (typaParamFound != null && typaParamFound) return true;
              }
            }
            return PsiUtil.resolveClassInType(classType) instanceof PsiTypeParameter;
          }

          @Override
          public Boolean visitWildcardType(PsiWildcardType wildcardType) {
            final PsiType bound = wildcardType.getBound();
            if (bound == null) return false;
            return bound.accept(this);
          }
        });
        if (accept != null && accept.booleanValue()) return true;
      }
    }
    return false;
  }
}

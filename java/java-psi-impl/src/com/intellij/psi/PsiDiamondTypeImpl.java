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

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Function;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * User: anna
 * Date: Jul 30, 2010
 */
public class PsiDiamondTypeImpl extends PsiDiamondType {
  private final PsiManager myManager;
  private final PsiTypeElement myTypeElement;
  private static final Logger LOG = Logger.getInstance("#" + PsiDiamondTypeImpl.class.getName());

  public PsiDiamondTypeImpl(PsiManager manager, PsiTypeElement psiTypeElement) {
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
  public <A> A accept(@NotNull PsiTypeVisitor<A> visitor) {
    return visitor.visitDiamondType(this);
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

  @Override
  public DiamondInferenceResult resolveInferredTypes() {
    final PsiNewExpression newExpression = PsiTreeUtil.getParentOfType(myTypeElement, PsiNewExpression.class);
    if (newExpression == null) {
      return PsiDiamondTypeImpl.DiamondInferenceResult.NULL_RESULT;
    }

    return resolveInferredTypes(newExpression);
  }

  public static DiamondInferenceResult resolveInferredTypes(PsiNewExpression newExpression) {
    return resolveInferredTypes(newExpression, newExpression);
  }

  public static DiamondInferenceResult resolveInferredTypes(PsiNewExpression newExpression,
                                                            PsiElement context) {
    final PsiAnonymousClass anonymousClass = newExpression.getAnonymousClass();
    if (anonymousClass != null) {
      final PsiElement resolve = anonymousClass.getBaseClassReference().resolve();
      if (resolve instanceof PsiClass) {
        return PsiDiamondTypeImpl.DiamondInferenceResult.ANONYMOUS_INNER_RESULT;
      }
    }

    final PsiReferenceParameterList referenceParameterList = PsiTreeUtil.getChildOfType(newExpression, PsiReferenceParameterList.class);
    if (referenceParameterList != null && referenceParameterList.getTypeParameterElements().length > 0) {
      return DiamondInferenceResult.EXPLICIT_CONSTRUCTOR_TYPE_ARGS;
    }

    return resolveInferredTypesNoCheck(newExpression, context);
  }

  public static DiamondInferenceResult resolveInferredTypesNoCheck(final PsiNewExpression newExpression, final PsiElement context) {
    final PsiClass psiClass = findClass(newExpression);
    if (psiClass == null) return DiamondInferenceResult.NULL_RESULT;
    final PsiExpressionList argumentList = newExpression.getArgumentList();
    if (argumentList == null) return DiamondInferenceResult.NULL_RESULT;
    final Ref<PsiMethod> staticFactoryRef = new Ref<PsiMethod>();
    final PsiSubstitutor inferredSubstitutor = ourDiamondGuard.doPreventingRecursion(context, false, new Computable<PsiSubstitutor>() {
      @Override
      public PsiSubstitutor compute() {
        final PsiMethod constructor = findConstructor(psiClass, newExpression);
        PsiTypeParameter[] params = getAllTypeParams(constructor, psiClass);

        final PsiMethod staticFactory = generateStaticFactory(constructor, psiClass, params);
        if (staticFactory == null) {
          return null;
        }
        staticFactoryRef.set(staticFactory);
        
        return inferTypeParametersForStaticFactory(staticFactory, newExpression, context);
      }
    });
    if (inferredSubstitutor == null) {
      return DiamondInferenceResult.NULL_RESULT;
    }
    final PsiMethod staticFactory = staticFactoryRef.get();
    if (staticFactory == null) {
      LOG.error(inferredSubstitutor);
      return DiamondInferenceResult.NULL_RESULT;
    }
    final PsiTypeParameter[] parameters = staticFactory.getTypeParameters();
    final PsiTypeParameter[] classParameters = psiClass.getTypeParameters();
    final PsiJavaCodeReferenceElement classOrAnonymousClassReference = newExpression.getClassOrAnonymousClassReference();
    LOG.assertTrue(classOrAnonymousClassReference != null);
    final DiamondInferenceResult
      result = new DiamondInferenceResult(classOrAnonymousClassReference.getReferenceName() + "<>", newExpression.getProject());
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
        final PsiExpression newExpressionQualifier = newExpression.getQualifier();
        final PsiElement qualifierElement = classReference.getQualifier();
        final String qualifier = qualifierElement != null ? qualifierElement.getText() : "";
        final String qualifiedName = StringUtil.getQualifiedName(qualifier, text);
        if (newExpressionQualifier != null) {
          final PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(newExpressionQualifier.getType());
          if (aClass != null) {
            return aClass.findInnerClassByName(qualifiedName, false);
          }
        }
        return resolveHelper.resolveReferencedClass(qualifiedName, newExpression);
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
        final String extendsList = psiTypeParameter.getLanguage().isKindOf(JavaLanguage.INSTANCE) ? psiTypeParameter.getExtendsList().getText() : null;
        return psiTypeParameter.getName() + (StringUtil.isEmpty(extendsList) ? "" : " " + extendsList);
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
                                                                    final PsiElement parent) {
    final PsiExpressionList argumentList = expression.getArgumentList();
    if (argumentList != null) {
      final MethodCandidateInfo staticFactoryCandidateInfo =
        new MethodCandidateInfo(staticFactoryMethod, PsiSubstitutor.EMPTY, false, false, argumentList, parent,
                                argumentList.getExpressionTypes(), null) {
          @Override
          protected PsiElement getParent() {
            return parent;
          }

          @Override
          protected PsiElement getMarkerList() {
            return parent instanceof PsiNewExpression ? ((PsiNewExpression)parent).getArgumentList() : super.getMarkerList();
          }
        };
      return staticFactoryCandidateInfo.getSubstitutor();
    }
    else {
      return PsiSubstitutor.EMPTY;
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
    for (final PsiMethod method : psiClass.getConstructors()) {
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
            final PsiClass aClass = PsiUtil.resolveClassInType(classType);
            return aClass instanceof PsiTypeParameter && ((PsiTypeParameter)aClass).getOwner() == method;
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

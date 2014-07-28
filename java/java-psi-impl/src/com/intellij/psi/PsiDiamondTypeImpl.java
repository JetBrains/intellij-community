/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.scope.PsiConflictResolver;
import com.intellij.psi.scope.conflictResolvers.JavaMethodsConflictResolver;
import com.intellij.psi.scope.processor.MethodCandidatesProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.VisibilityUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author anna
 * @since Jul 30, 2010
 */
public class PsiDiamondTypeImpl extends PsiDiamondType {
  private static final Logger LOG = Logger.getInstance("#" + PsiDiamondTypeImpl.class.getName());

  private final PsiManager myManager;
  private final PsiTypeElement myTypeElement;

  public PsiDiamondTypeImpl(PsiManager manager, PsiTypeElement psiTypeElement) {
    super(PsiAnnotation.EMPTY_ARRAY);
    myManager = manager;
    myTypeElement = psiTypeElement;
  }

  @NotNull
  @Override
  public String getPresentableText() {
    return "";
  }

  @NotNull
  @Override
  public String getCanonicalText() {
    return "";
  }

  @NotNull
  @Override
  public String getInternalCanonicalText() {
    return "Diamond Type";
  }

  @Override
  public boolean isValid() {
    return false;
  }

  @Override
  public boolean equalsToText(@NotNull @NonNls String text) {
    return text.isEmpty();
  }

  @Override
  public <A> A accept(@NotNull PsiTypeVisitor<A> visitor) {
    return visitor.visitDiamondType(this);
  }

  @NotNull
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
        final PsiMethod staticFactory = findConstructorStaticFactory(psiClass, newExpression);
        if (staticFactory == null) {
          return null;
        }
        staticFactoryRef.set(staticFactory);

        return inferTypeParametersForStaticFactory(staticFactory, newExpression, context, false);
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
    final DiamondInferenceResult result = new DiamondInferenceResult(classOrAnonymousClassReference.getReferenceName() + "<>");

    if (PsiUtil.isRawSubstitutor(staticFactory, inferredSubstitutor)) {
      return result;
    }

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
  private static PsiMethod findConstructorStaticFactory(final PsiClass containingClass, PsiNewExpression newExpression) {
    final PsiExpressionList argumentList = newExpression.getArgumentList();
    if (argumentList == null) return null;

    final LanguageLevel languageLevel = PsiUtil.getLanguageLevel(newExpression);
    final List<CandidateInfo> conflicts = new ArrayList<CandidateInfo>();
    PsiMethod[] constructors = containingClass.getConstructors();
    if (constructors.length == 0) {
      //default constructor
      constructors = new PsiMethod[] {null};
    }

    final PsiConflictResolver[] conflictResolvers = {new JavaMethodsConflictResolver(argumentList, languageLevel)};
    final MethodCandidatesProcessor processor = new MethodCandidatesProcessor(argumentList, argumentList.getContainingFile(), conflictResolvers, conflicts) {
      @Override
      protected boolean isAccepted(PsiMethod candidate) {
        return true;
      }

      @Override
      protected PsiClass getContainingClass(PsiMethod method) {
        return containingClass;
      }
    };
    processor.setArgumentList(argumentList);

    for (PsiMethod constructor : constructors) {
      final PsiTypeParameter[] params = getAllTypeParams(constructor, containingClass);
      final PsiMethod staticFactory = generateStaticFactory(constructor, containingClass, params, newExpression.getClassReference());
      if (staticFactory != null) {
        processor.add(staticFactory, PsiSubstitutor.EMPTY);
      }
    }

    final JavaResolveResult[] result = processor.getResult();
    return result.length == 1 ? (PsiMethod)result[0].getElement() : null;
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
  private static PsiMethod generateStaticFactory(@Nullable PsiMethod constructor,
                                                 PsiClass containingClass,
                                                 PsiTypeParameter[] params,
                                                 PsiJavaCodeReferenceElement reference) {
    final StringBuilder buf = new StringBuilder();
    final String modifier = VisibilityUtil.getVisibilityModifier(constructor != null ? constructor.getModifierList() : containingClass.getModifierList());
    if (!PsiModifier.PACKAGE_LOCAL.equals(modifier)) {
      buf.append(modifier);
      buf.append(" ");
    }
    buf.append("static ");
    buf.append("<");
    buf.append(StringUtil.join(params, new Function<PsiTypeParameter, String>() {
      @Override
      public String fun(PsiTypeParameter psiTypeParameter) {
        String extendsList = "";
        if (psiTypeParameter.getLanguage().isKindOf(JavaLanguage.INSTANCE)) {
          final PsiClassType[] extendsListTypes = psiTypeParameter.getExtendsListTypes();
          if (extendsListTypes.length > 0) {
            final Function<PsiClassType, String> canonicalTypePresentationFun = new Function<PsiClassType, String>() {
              @Override
              public String fun(PsiClassType type) {
                return type.getCanonicalText();
              }
            };
            extendsList = " extends " + StringUtil.join(extendsListTypes, canonicalTypePresentationFun, "&");
          }
        }
        return psiTypeParameter.getName() + extendsList;
      }
    }, ", "));
    buf.append(">");

    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(containingClass.getProject());

    String qualifiedName = containingClass.getQualifiedName();

    PsiElement qualifier = reference != null ? reference.getQualifier() : null;
    if (qualifier instanceof PsiJavaCodeReferenceElement) {
      final JavaResolveResult resolveResult = ((PsiJavaCodeReferenceElement)qualifier).advancedResolve(false);
      final PsiElement element = resolveResult.getElement();
      if (element instanceof PsiClass) {
        final String outerClassSubstitutedQName =
          elementFactory.createType((PsiClass)element, resolveResult.getSubstitutor()).getInternalCanonicalText();
        qualifiedName = outerClassSubstitutedQName + "." + containingClass.getName();
      }
    } else if (reference != null && qualifier == null && containingClass.getContainingClass() != null) {
      qualifiedName = null;
    }

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

    try {
      return elementFactory.createMethodFromText(buf.toString(), constructor != null ? constructor : containingClass);
    }
    catch (IncorrectOperationException e) {
      return null;
    }
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
                                                                    final PsiElement parent,
                                                                    final boolean varargs) {
    final PsiExpressionList argumentList = expression.getArgumentList();
    if (argumentList != null) {
      final MethodCandidateInfo staticFactoryCandidateInfo =
        new MethodCandidateInfo(staticFactoryMethod, PsiSubstitutor.EMPTY, false, false, argumentList, parent,
                                argumentList.getExpressionTypes(), null) {
          @Override
          public boolean isVarargs() {
            return varargs;
          }

          @Override
          protected PsiElement getParent() {
            return parent;
          }

          @Override
          protected PsiElement getMarkerList() {
            return parent instanceof PsiNewExpression ? ((PsiNewExpression)parent).getArgumentList() : super.getMarkerList();
          }
        };
      if (!varargs && staticFactoryMethod.isVarArgs() && staticFactoryCandidateInfo.getPertinentApplicabilityLevel() < MethodCandidateInfo.ApplicabilityLevel.FIXED_ARITY) {
        return inferTypeParametersForStaticFactory(staticFactoryMethod, expression, parent, true);
      }
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
                final Boolean typeParamFound = psiType.accept(this);
                if (typeParamFound != null && typeParamFound) return true;
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

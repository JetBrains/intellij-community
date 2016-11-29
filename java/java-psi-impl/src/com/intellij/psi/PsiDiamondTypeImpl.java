/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.JavaVersionService;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceSession;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.scope.PsiConflictResolver;
import com.intellij.psi.scope.conflictResolvers.JavaMethodsConflictResolver;
import com.intellij.psi.scope.processor.MethodCandidatesProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.*;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.text.UniqueNameGenerator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author anna
 * @since Jul 30, 2010
 */
public class PsiDiamondTypeImpl extends PsiDiamondType {
  private static final Logger LOG = Logger.getInstance(PsiDiamondTypeImpl.class);

  private final PsiManager myManager;
  private final PsiTypeElement myTypeElement;

  public PsiDiamondTypeImpl(PsiManager manager, PsiTypeElement psiTypeElement) {
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
    final PsiNewExpression newExpression = getNewExpression();
    if (newExpression == null) {
      return PsiDiamondTypeImpl.DiamondInferenceResult.NULL_RESULT;
    }

    return resolveInferredTypes(newExpression);
  }

  private PsiNewExpression getNewExpression() {
    PsiElement typeElementWithDiamondTypeArgument = myTypeElement.getParent();
    return PsiTreeUtil.getParentOfType(typeElementWithDiamondTypeArgument, PsiNewExpression.class, true, PsiTypeElement.class);
  }

  @Nullable
  @Override
  public JavaResolveResult getStaticFactory() {
    final PsiNewExpression newExpression = getNewExpression();
    return newExpression != null ? getStaticFactory(newExpression, newExpression) : null;
  }

  public static DiamondInferenceResult resolveInferredTypes(PsiNewExpression newExpression) {
    return resolveInferredTypes(newExpression, newExpression);
  }

  public static DiamondInferenceResult resolveInferredTypes(PsiNewExpression newExpression,
                                                            PsiElement context) {
    final PsiAnonymousClass anonymousClass = newExpression.getAnonymousClass();
    if (anonymousClass != null && !PsiUtil.isLanguageLevel9OrHigher(newExpression)) {
      final PsiElement resolve = anonymousClass.getBaseClassReference().resolve();
      if (resolve instanceof PsiClass) {
        return PsiDiamondTypeImpl.DiamondInferenceResult.ANONYMOUS_INNER_RESULT;
      }
    }

    final PsiReferenceParameterList referenceParameterList = PsiTreeUtil.getChildOfType(newExpression, PsiReferenceParameterList.class);
    if (referenceParameterList != null && referenceParameterList.getTypeParameterElements().length > 0) {
      return DiamondInferenceResult.EXPLICIT_CONSTRUCTOR_TYPE_ARGS;
    }

    final DiamondInferenceResult inferenceResult = resolveInferredTypesNoCheck(newExpression, context);
    if (anonymousClass != null && PsiUtil.isLanguageLevel9OrHigher(newExpression)) {
      final InferredAnonymousTypeVisitor anonymousTypeVisitor = new InferredAnonymousTypeVisitor(context);
      for (PsiType type : inferenceResult.getInferredTypes()) {
        final Boolean accepted = type.accept(anonymousTypeVisitor);
        if (accepted != null && !accepted.booleanValue()) {
          return PsiDiamondTypeImpl.DiamondInferenceResult.ANONYMOUS_INNER_RESULT;
        } 
      }
    }
    return inferenceResult;
  }

  private static JavaResolveResult getStaticFactory(final PsiNewExpression newExpression, final PsiElement context) {
    return context == newExpression
           ? CachedValuesManager.getCachedValue(newExpression, new CachedValueProvider<JavaResolveResult>() {
                @Nullable
                @Override
                public Result<JavaResolveResult> compute() {
                  return new Result<JavaResolveResult>(getStaticFactoryCandidateInfo(newExpression, newExpression),
                                                       PsiModificationTracker.MODIFICATION_COUNT);
                }
              })
           : getStaticFactoryCandidateInfo(newExpression, context);
  }

  public static DiamondInferenceResult resolveInferredTypesNoCheck(final PsiNewExpression newExpression, final PsiElement context) {
    final JavaResolveResult staticFactoryCandidateInfo = getStaticFactory(newExpression, context);
    if (staticFactoryCandidateInfo == null) {
      return DiamondInferenceResult.NULL_RESULT;
    }
    final PsiSubstitutor inferredSubstitutor = ourDiamondGuard.doPreventingRecursion(context, false, new Computable<PsiSubstitutor>() {
      @Override
      public PsiSubstitutor compute() {
        PsiSubstitutor substitutor = staticFactoryCandidateInfo.getSubstitutor();
        return staticFactoryCandidateInfo instanceof MethodCandidateInfo &&
               ((MethodCandidateInfo)staticFactoryCandidateInfo).getInferenceErrorMessage() != null
               ? null : substitutor;
      }
    });
    if (inferredSubstitutor == null) {
      return DiamondInferenceResult.NULL_RESULT;
    }

    if (!(staticFactoryCandidateInfo instanceof MethodCandidateInfo)) {
      return DiamondInferenceResult.UNRESOLVED_CONSTRUCTOR;
    }

    //15.9.3 Choosing the Constructor and its Arguments
    //The return type and throws clause of cj are the same as the return type and throws clause determined for mj (§15.12.2.6)
    if (InferenceSession.wasUncheckedConversionPerformed(context)) {
      return DiamondInferenceResult.RAW_RESULT;
    }

    final PsiMethod staticFactory = ((MethodCandidateInfo)staticFactoryCandidateInfo).getElement();
    final PsiTypeParameter[] parameters = staticFactory.getTypeParameters();
    final PsiElement staticFactoryContext = staticFactory.getContext();
    final PsiClass psiClass = PsiTreeUtil.getContextOfType(staticFactoryContext, PsiClass.class, false);
    if (psiClass == null) {
      LOG.error("failed for expression:" + newExpression);
      return DiamondInferenceResult.NULL_RESULT;
    }
    final PsiTypeParameter[] classParameters = psiClass.getTypeParameters();
    final PsiJavaCodeReferenceElement classOrAnonymousClassReference = newExpression.getClassOrAnonymousClassReference();
    LOG.assertTrue(classOrAnonymousClassReference != null);
    final DiamondInferenceResult result = new DiamondInferenceResult(classOrAnonymousClassReference.getReferenceName() + "<>");

    if (PsiUtil.isRawSubstitutor(staticFactory, inferredSubstitutor)) {
      if (!JavaVersionService.getInstance().isAtLeast(newExpression, JavaSdkVersion.JDK_1_8) && 
          PsiUtil.skipParenthesizedExprUp(newExpression.getParent()) instanceof PsiExpressionList) {
        for (PsiTypeParameter ignored : parameters) {
          result.addInferredType(PsiType.getJavaLangObject(newExpression.getManager(), GlobalSearchScope.allScope(newExpression.getProject())));
        }
      }
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

  private static JavaResolveResult getStaticFactoryCandidateInfo(final PsiNewExpression newExpression,
                                                                 final PsiElement context) {
    return ourDiamondGuard.doPreventingRecursion(context, false, new Computable<JavaResolveResult>() {
      @Override
      public JavaResolveResult compute() {

        final PsiExpressionList argumentList = newExpression.getArgumentList();
        if (argumentList == null) {
          //token expected diagnostic is provided by parser
          return null;
        }

        final JavaMethodsConflictResolver resolver = new JavaMethodsConflictResolver(argumentList, PsiUtil.getLanguageLevel(newExpression));
        final JavaResolveResult[] result = collectStaticFactories(newExpression, resolver);
        final PsiMethod staticFactory = result != null && result.length == 1 ? (PsiMethod)result[0].getElement() : null;
        if (staticFactory == null) {
          //additional diagnostics: inference fails due to unresolved constructor
          return JavaResolveResult.EMPTY;
        }

        final MethodCandidateInfo staticFactoryCandidateInfo = createMethodCandidate(staticFactory, context, false, argumentList);
        if (!staticFactory.isVarArgs()) {
          return staticFactoryCandidateInfo;
        }

        final ArrayList<CandidateInfo> conflicts = new ArrayList<CandidateInfo>();
        conflicts.add(staticFactoryCandidateInfo);
        conflicts.add(createMethodCandidate(staticFactory, context, true, argumentList));
        return resolver.resolveConflict(conflicts);
      }
    });
  }

  @Nullable
  public static JavaResolveResult[] collectStaticFactories(PsiNewExpression newExpression, final PsiConflictResolver... conflictResolvers) {
    PsiExpressionList argumentList = newExpression.getArgumentList();
    if (argumentList == null) {
      return null;
    }

    final PsiClass psiClass = findClass(newExpression);
    if (psiClass == null) {
      //should not happens: unresolved class reference would be first and inference won't start
      return null;
    }

    final List<CandidateInfo> candidates = new ArrayList<CandidateInfo>();
    PsiMethod[] constructors = psiClass.getConstructors();
    if (constructors.length == 0) {
      //default constructor
      constructors = new PsiMethod[] {null};
    }

    final MethodCandidatesProcessor
      processor = new MethodCandidatesProcessor(argumentList, argumentList.getContainingFile(), conflictResolvers, candidates) {
      @Override
      protected boolean isAccepted(PsiMethod candidate) {
        return true;
      }

      @Override
      protected PsiClass getContainingClass(PsiMethod method) {
        return psiClass;
      }

      @Override
      protected boolean acceptVarargs() {
        return true;
      }
    };
    processor.setArgumentList(argumentList);

    for (PsiMethod constructor : constructors) {
      final PsiTypeParameter[] params = getAllTypeParams(constructor, psiClass);
      final PsiMethod staticFactory = generateStaticFactory(constructor, psiClass, params, newExpression.getClassReference());
      if (staticFactory != null) {
        processor.add(staticFactory, PsiSubstitutor.EMPTY);
      }
    }

    return processor.getResult();
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
    //it's possible that constructor type parameters and class type parameters are same named:
    //it's important that class type parameters names are preserved(they are first in the list),
    //though constructor parameters would be renamed in case of conflicts
    final UniqueNameGenerator generator = new UniqueNameGenerator();
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
        return generator.generateUniqueName(psiTypeParameter.getName()) + extendsList;
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
        int myIdx;
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
    Collections.addAll(params, containingClass.getTypeParameters());
    if (listOwner != null) {
      Collections.addAll(params, listOwner.getTypeParameters());
    }
    return params.toArray(new PsiTypeParameter[params.size()]);
  }


  private static MethodCandidateInfo createMethodCandidate(@NotNull final PsiMethod staticFactoryMethod,
                                                           final PsiElement parent,
                                                           final boolean varargs,
                                                           final PsiExpressionList argumentList) {
    return new MethodCandidateInfo(staticFactoryMethod, PsiSubstitutor.EMPTY, false, false, argumentList, parent, null, null) {
      private PsiType[] myExpressionTypes;

      @Override
      public boolean isVarargs() {
        return varargs;
      }

      @Override
      protected PsiElement getParent() {
        return parent;
      }

      @Override
      public PsiType[] getArgumentTypes() {
        if (myExpressionTypes == null) {
          final PsiType[] expressionTypes = argumentList.getExpressionTypes();
          if (MethodCandidateInfo.isOverloadCheck() || LambdaUtil.isLambdaParameterCheck()) {
            return expressionTypes;
          }
          myExpressionTypes = expressionTypes;
        }
        return myExpressionTypes;
      }

      @Override
      protected PsiElement getMarkerList() {
        return parent instanceof PsiNewExpression ? ((PsiNewExpression)parent).getArgumentList() : super.getMarkerList();
      }
    };
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

  /**
   * from JDK-8062373 Allow diamond to be used with anonymous classes
   * It is a compile-time error if the superclass or superinterface type of the anonymous class, T, or any subexpression of T, has one of the following forms:
   *  - A type variable (4.4) that was not declared as a type parameter (such as a type variable produced by capture conversion (5.1.10)) 
   *  - An intersection type (4.9) 
   *  - A class or interface type, where the class or interface declaration is not accessible from the class or interface in which the expression appears.
   * 
   * The term "subexpression" includes type arguments of parameterized types (4.5), bounds of wildcards (4.5.1), and element types of array types (10.1).
   * It excludes bounds of type variables.
   */
  private static class InferredAnonymousTypeVisitor extends PsiTypeVisitor<Boolean> {
    private final PsiElement myExpression;

    public InferredAnonymousTypeVisitor(PsiElement expression) {
      myExpression = expression;
    }

    @Nullable
    @Override
    public Boolean visitType(PsiType type) {
      return true;
    }

    @Nullable
    @Override
    public Boolean visitCapturedWildcardType(PsiCapturedWildcardType capturedWildcardType) {
      return false;
    }

    @Nullable
    @Override
    public Boolean visitIntersectionType(PsiIntersectionType intersectionType) {
      return false;
    }

    @Nullable
    @Override
    public Boolean visitClassType(PsiClassType classType) {
      final PsiClassType.ClassResolveResult resolveResult = classType.resolveGenerics();
      final PsiClass psiClass = resolveResult.getElement();
      if (psiClass != null) {
        if (psiClass instanceof PsiTypeParameter && TypeConversionUtil.isFreshVariable((PsiTypeParameter)psiClass)) {
          return false;
        }
        
        if (!PsiUtil.isAccessible(psiClass, myExpression, null)) {
          return false;
        }
        
        for (PsiType psiType : resolveResult.getSubstitutor().getSubstitutionMap().values()) {
          final Boolean accepted = psiType != null ? psiType.accept(this) : null;
          if (accepted != null && !accepted.booleanValue()) {
            return false;
          }
        }
      }
      return true;
    }
  }
}
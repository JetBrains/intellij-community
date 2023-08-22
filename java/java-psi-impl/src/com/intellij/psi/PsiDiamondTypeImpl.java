// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.source.tree.java.PsiMethodCallExpressionImpl;
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

  @Override
  public PsiType @NotNull [] getSuperTypes() {
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
    PsiNewExpression newExpression =
      PsiTreeUtil.getParentOfType(typeElementWithDiamondTypeArgument, PsiNewExpression.class, true, PsiTypeElement.class, PsiStatement.class);
    if (newExpression != null) {
      PsiJavaCodeReferenceElement classReference = newExpression.getClassOrAnonymousClassReference();
      if (classReference != null && classReference.getParameterList() == typeElementWithDiamondTypeArgument) {
        return newExpression;
      }
    }
    return null;
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
    return context == newExpression && !MethodCandidateInfo.isOverloadCheck(newExpression.getArgumentList())
           ? CachedValuesManager.getCachedValue(newExpression,
                                                () -> new CachedValueProvider.Result<>(
                                                  getStaticFactoryCandidateInfo(newExpression, newExpression),
                                                  PsiModificationTracker.MODIFICATION_COUNT))
           : getStaticFactoryCandidateInfo(newExpression, context);
  }

  public static DiamondInferenceResult resolveInferredTypesNoCheck(final PsiNewExpression newExpression, final PsiElement context) {
    final JavaResolveResult staticFactoryCandidateInfo = getStaticFactory(newExpression, context);
    if (staticFactoryCandidateInfo == null) {
      return DiamondInferenceResult.NULL_RESULT;
    }
    if (!(staticFactoryCandidateInfo instanceof MethodCandidateInfo)) {
      return DiamondInferenceResult.UNRESOLVED_CONSTRUCTOR;
    }

    LOG.assertTrue(!PsiMethodCallExpressionImpl.doWePerformGenericMethodOverloadResolutionNow(newExpression, PsiUtil.getLanguageLevel(newExpression)), 
                   "diamond evaluation during overload resolution");

    PsiSubstitutor substitutor = staticFactoryCandidateInfo.getSubstitutor();
    String errorMessage = ((MethodCandidateInfo)staticFactoryCandidateInfo).getInferenceErrorMessageAssumeAlreadyComputed();

    //15.9.3 Choosing the Constructor and its Arguments
    //The return type and throws clause of cj are the same as the return type and throws clause determined for mj (p15.12.2.6)
    if (errorMessage == null && ((MethodCandidateInfo)staticFactoryCandidateInfo).isErased()) {
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
    final DiamondInferenceResult result = new DiamondInferenceResult(classOrAnonymousClassReference.getReferenceName() + "<>") {
      @Override
      public String getErrorMessage() {
        return errorMessage != null ? DiamondInferenceResult.NULL_RESULT.getErrorMessage() : super.getErrorMessage();
      }
    };

    if (errorMessage == null && PsiUtil.isRawSubstitutor(staticFactory, substitutor)) {
      //http://www.oracle.com/technetwork/java/javase/8-compatibility-guide-2156366.html#A999198 REF 7144506
      if (!PsiUtil.isLanguageLevel8OrHigher(newExpression) && PsiUtil.skipParenthesizedExprUp(newExpression.getParent()) instanceof PsiExpressionList) {
        for (PsiTypeParameter ignored : parameters) {
          result.addInferredType(PsiType.getJavaLangObject(newExpression.getManager(), GlobalSearchScope.allScope(newExpression.getProject())));
        }
      }
      return result;
    }

    for (PsiTypeParameter parameter : parameters) {
      for (PsiTypeParameter classParameter : classParameters) {
        if (Comparing.strEqual(classParameter.getName(), parameter.getName())) {
          result.addInferredType(substitutor.substitute(parameter));
          break;
        }
      }
    }
    return result;
  }

  private static JavaResolveResult getStaticFactoryCandidateInfo(@NotNull PsiNewExpression newExpression,
                                                                 final PsiElement context) {
    return ourDiamondGuard.doPreventingRecursion(context, false, () -> {

      final PsiExpressionList argumentList = newExpression.getArgumentList();
      if (argumentList == null) {
        //token expected diagnostic is provided by parser
        return null;
      }

      PsiFile containingFile = argumentList.getContainingFile();
      if (containingFile == null) {
        return null;
      }
      JavaMethodsConflictResolver resolver = new JavaMethodsConflictResolver(argumentList, null,
                                                                             PsiUtil.getLanguageLevel(containingFile),
                                                                             containingFile);
      final List<CandidateInfo> results = collectStaticFactories(newExpression);
      CandidateInfo result = results != null ? resolver.resolveConflict(new ArrayList<>(results)) : null;
      final PsiMethod staticFactory = result != null ? (PsiMethod)result.getElement() : null;
      if (staticFactory == null) {
        //additional diagnostics: inference fails due to unresolved constructor
        return JavaResolveResult.EMPTY;
      }

      final MethodCandidateInfo staticFactoryCandidateInfo = createMethodCandidate((MethodCandidateInfo)result, context, false, argumentList);
      if (!staticFactory.isVarArgs()) {
        return staticFactoryCandidateInfo;
      }

      final ArrayList<CandidateInfo> conflicts = new ArrayList<>();
      conflicts.add(staticFactoryCandidateInfo);
      conflicts.add(createMethodCandidate((MethodCandidateInfo)result, context, true, argumentList));
      return resolver.resolveConflict(conflicts);
    });
  }

  @Nullable
  public static List<CandidateInfo> collectStaticFactories(PsiNewExpression newExpression) {
    return CachedValuesManager.getCachedValue(newExpression,
                                              () -> new CachedValueProvider.Result<>(collectStaticFactoriesInner(newExpression),
                                                                                     PsiModificationTracker.MODIFICATION_COUNT));
  }

  private static List<CandidateInfo> collectStaticFactoriesInner(PsiNewExpression newExpression) {
    PsiExpressionList argumentList = newExpression.getArgumentList();
    if (argumentList == null) {
      return null;
    }

    final PsiClass psiClass = findClass(newExpression);
    if (psiClass == null) {
      //should not happens: unresolved class reference would be first and inference won't start
      return null;
    }

    final List<CandidateInfo> candidates = new ArrayList<>();
    PsiMethod[] constructors = psiClass.getConstructors();
    if (constructors.length == 0) {
      //default constructor
      constructors = new PsiMethod[] {null};
    }

    PsiFile containingFile = argumentList.getContainingFile();
    final MethodCandidatesProcessor
      processor = new MethodCandidatesProcessor(argumentList, containingFile, new PsiConflictResolver[0], candidates) {
      @Override
      protected boolean isAccepted(@NotNull PsiMethod candidate) {
        return true;
      }

      @Override
      protected PsiClass getContainingClass(@NotNull PsiMethod method) {
        if (newExpression.getAnonymousClass() != null) {
          return PsiTreeUtil.getContextOfType(argumentList, PsiClass.class, false);
        }
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

    return processor.getResults();
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
    buf.append(StringUtil.join(params, psiTypeParameter -> {
      String extendsList = "";
      if (psiTypeParameter.getLanguage().isKindOf(JavaLanguage.INSTANCE)) {
        final PsiClassType[] extendsListTypes = psiTypeParameter.getExtendsListTypes();
        if (extendsListTypes.length > 0) {
          final Function<PsiClassType, String> canonicalTypePresentationFun = type -> type.getCanonicalText();
          extendsList = " extends " + StringUtil.join(extendsListTypes, canonicalTypePresentationFun, "&");
        }
      }
      return generator.generateUniqueName(psiTypeParameter.getName()) + extendsList;
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
    buf.append(StringUtil.join(parameters, psiTypeParameter -> psiTypeParameter.getName(), ", "));
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
      PsiClassType[] types = constructor.getThrowsList().getReferencedTypes();
      if (types.length > 0) {
        buf.append("throws ").append(StringUtil.join(types, type -> type.getCanonicalText(), ", "));
      }
    }
    buf.append("{}");

    try {
      return elementFactory.createMethodFromText(buf.toString(), constructor != null ? constructor : containingClass);
    }
    catch (IncorrectOperationException e) {
      return null;
    }
  }

  private static PsiTypeParameter @NotNull [] getAllTypeParams(PsiTypeParameterListOwner listOwner, PsiClass containingClass) {
    Set<PsiTypeParameter> params = new LinkedHashSet<>();
    Collections.addAll(params, containingClass.getTypeParameters());
    if (listOwner != null) {
      Collections.addAll(params, listOwner.getTypeParameters());
    }
    return params.toArray(PsiTypeParameter.EMPTY_ARRAY);
  }


  private static MethodCandidateInfo createMethodCandidate(@NotNull final MethodCandidateInfo staticFactoryMethod,
                                                           final PsiElement parent,
                                                           final boolean varargs,
                                                           final PsiExpressionList argumentList) {
    return new MethodCandidateInfo(staticFactoryMethod.getElement(), PsiSubstitutor.EMPTY, !staticFactoryMethod.isAccessible(), false, argumentList, parent, null, null) {
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
          if (MethodCandidateInfo.isOverloadCheck()) {
            return expressionTypes;
          }
          myExpressionTypes = expressionTypes;
        }
        return myExpressionTypes;
      }
    };
  }

  public static boolean hasDefaultConstructor(@NotNull final PsiClass psiClass) {
    final PsiMethod[] constructors = psiClass.getConstructors();
    for (PsiMethod method : constructors) {
      if (method.getParameterList().isEmpty()) return true;
    }
    return constructors.length == 0;
  }

  public static boolean haveConstructorsGenericsParameters(@NotNull final PsiClass psiClass) {
    for (final PsiMethod method : psiClass.getConstructors()) {
      for (PsiParameter parameter : method.getParameterList().getParameters()) {
        final PsiType type = parameter.getType();
        final Boolean accept = type.accept(new PsiTypeVisitor<Boolean>() {
          @Override
          public Boolean visitArrayType(@NotNull PsiArrayType arrayType) {
            return arrayType.getComponentType().accept(this);
          }

          @Override
          public Boolean visitClassType(@NotNull PsiClassType classType) {
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
          public Boolean visitWildcardType(@NotNull PsiWildcardType wildcardType) {
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

    InferredAnonymousTypeVisitor(PsiElement expression) {
      myExpression = expression;
    }

    @Nullable
    @Override
    public Boolean visitType(@NotNull PsiType type) {
      return true;
    }

    @Nullable
    @Override
    public Boolean visitCapturedWildcardType(@NotNull PsiCapturedWildcardType capturedWildcardType) {
      return false;
    }

    @Nullable
    @Override
    public Boolean visitIntersectionType(@NotNull PsiIntersectionType intersectionType) {
      return false;
    }

    @Nullable
    @Override
    public Boolean visitClassType(@NotNull PsiClassType classType) {
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
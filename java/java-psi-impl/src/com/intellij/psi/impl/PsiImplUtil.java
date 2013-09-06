/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.impl.light.LightClassReference;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.impl.source.PsiImmediateClassType;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.processor.FilterScopeProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.PackageScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PairFunction;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.intellij.psi.PsiAnnotation.TargetType;

public class PsiImplUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.PsiImplUtil");

  private static final Set<TargetType> DEFAULT_TARGETS = Collections.unmodifiableSet(ContainerUtil.newHashSet(
    TargetType.PACKAGE, TargetType.TYPE, TargetType.ANNOTATION_TYPE,
    TargetType.FIELD, TargetType.METHOD, TargetType.CONSTRUCTOR,
    TargetType.PARAMETER, TargetType.LOCAL_VARIABLE));

  private static final TargetType[] PACKAGE_TARGETS = {TargetType.PACKAGE};
  private static final TargetType[] TYPE_USE_TARGETS = {TargetType.TYPE_USE};
  private static final TargetType[] ANNOTATION_TARGETS = {TargetType.ANNOTATION_TYPE, TargetType.TYPE, TargetType.TYPE_USE};
  private static final TargetType[] TYPE_TARGETS = {TargetType.TYPE, TargetType.TYPE_USE};
  private static final TargetType[] TYPE_PARAMETER_TARGETS = {TargetType.TYPE_PARAMETER, TargetType.TYPE_USE};
  private static final TargetType[] CONSTRUCTOR_TARGETS = {TargetType.CONSTRUCTOR, TargetType.TYPE_USE};
  private static final TargetType[] METHOD_TARGETS = {TargetType.METHOD, TargetType.TYPE_USE};
  private static final TargetType[] FIELD_TARGETS = {TargetType.FIELD, TargetType.TYPE_USE};
  private static final TargetType[] PARAMETER_TARGETS = {TargetType.PARAMETER, TargetType.TYPE_USE};
  private static final TargetType[] LOCAL_VARIABLE_TARGETS ={TargetType.LOCAL_VARIABLE, TargetType.TYPE_USE};

  private PsiImplUtil() { }

  @NotNull
  public static PsiMethod[] getConstructors(@NotNull PsiClass aClass) {
    final List<PsiMethod> constructorsList = new SmartList<PsiMethod>();

    final PsiMethod[] methods = aClass.getMethods();
    for (final PsiMethod method : methods) {
      if (method.isConstructor()) constructorsList.add(method);
    }

    return constructorsList.toArray(new PsiMethod[constructorsList.size()]);
  }

  @Nullable
  public static PsiAnnotationMemberValue findDeclaredAttributeValue(@NotNull PsiAnnotation annotation, @NonNls String attributeName) {
    if ("value".equals(attributeName)) attributeName = null;
    PsiNameValuePair[] attributes = annotation.getParameterList().getAttributes();
    for (PsiNameValuePair attribute : attributes) {
      @NonNls final String name = attribute.getName();
      if (Comparing.equal(name, attributeName) || attributeName == null && PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME.equals(name)) {
        return attribute.getValue();
      }
    }
    return null;
  }

  @Nullable
  public static PsiAnnotationMemberValue findAttributeValue(@NotNull PsiAnnotation annotation, @Nullable @NonNls String attributeName) {
    final PsiAnnotationMemberValue value = findDeclaredAttributeValue(annotation, attributeName);
    if (value != null) return value;

    if (attributeName == null) attributeName = "value";
    final PsiJavaCodeReferenceElement referenceElement = annotation.getNameReferenceElement();
    if (referenceElement != null) {
      PsiElement resolved = referenceElement.resolve();
      if (resolved != null) {
        PsiMethod[] methods = ((PsiClass)resolved).getMethods();
        for (PsiMethod method : methods) {
          if (PsiUtil.isAnnotationMethod(method) && Comparing.equal(method.getName(), attributeName)) {
            return ((PsiAnnotationMethod)method).getDefaultValue();
          }
        }
      }
    }
    return null;
  }

  @NotNull
  public static PsiTypeParameter[] getTypeParameters(@NotNull PsiTypeParameterListOwner owner) {
    final PsiTypeParameterList typeParameterList = owner.getTypeParameterList();
    if (typeParameterList != null) {
      return typeParameterList.getTypeParameters();
    }
    return PsiTypeParameter.EMPTY_ARRAY;
  }

  @NotNull
  public static PsiJavaCodeReferenceElement[] namesToPackageReferences(@NotNull PsiManager manager, @NotNull String[] names) {
    PsiJavaCodeReferenceElement[] refs = new PsiJavaCodeReferenceElement[names.length];
    for (int i = 0; i < names.length; i++) {
      String name = names[i];
      try {
        refs[i] = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory().createPackageReferenceElement(name);
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }
    return refs;
  }

  public static int getParameterIndex(@NotNull PsiParameter parameter, @NotNull PsiParameterList parameterList) {
    PsiElement parameterParent = parameter.getParent();
    assert parameterParent == parameterList : parameterList +"; "+parameterParent;
    PsiParameter[] parameters = parameterList.getParameters();
    for (int i = 0; i < parameters.length; i++) {
      PsiParameter paramInList = parameters[i];
      if (parameter.equals(paramInList)) return i;
    }
    String name = parameter.getName();
    PsiParameter suspect = null;
    int i;
    for (i = parameters.length - 1; i >= 0; i--) {
      PsiParameter paramInList = parameters[i];
      if (Comparing.equal(name, paramInList.getName())) {
        suspect = paramInList;
        break;
      }
    }
    String message = parameter + ":" + parameter.getClass() + " not found among parameters: " + Arrays.asList(parameters) + "." +
                     " parameterList' parent: " + parameterList.getParent() + ";" +
                     " parameter.isValid()=" + parameter.isValid() + ";" +
                     " parameterList.isValid()= " + parameterList.isValid() + ";" +
                     " parameterList stub: " + (parameterList instanceof StubBasedPsiElement ? ((StubBasedPsiElement)parameterList).getStub() : "---") + "; " +
                     " parameter stub: "+(parameter instanceof StubBasedPsiElement ? ((StubBasedPsiElement)parameter).getStub() : "---") + ";" +
                     " suspect: " + suspect +" (index="+i+"); " + (suspect==null?null:suspect.getClass()) +
                     " suspect stub: "+(suspect instanceof StubBasedPsiElement ? ((StubBasedPsiElement)suspect).getStub() : suspect == null ? "-null-" : "---"+suspect.getClass()) + ";" +
                     " parameter.equals(suspect) = " + parameter.equals(suspect) + "; " +
                     " parameter.getNode() == suspect.getNode():  " + (parameter.getNode() == (suspect==null ? null : suspect.getNode())) + "; " +
                     "."
      ;
    LOG.error(message);
    return i;
  }

  public static int getTypeParameterIndex(@NotNull PsiTypeParameter typeParameter, @NotNull PsiTypeParameterList typeParameterList) {
    PsiTypeParameter[] typeParameters = typeParameterList.getTypeParameters();
    for (int i = 0; i < typeParameters.length; i++) {
      if (typeParameter.equals(typeParameters[i])) return i;
    }
    LOG.assertTrue(false);
    return -1;
  }

  @NotNull
  public static Object[] getReferenceVariantsByFilter(@NotNull PsiJavaCodeReferenceElement reference, @NotNull ElementFilter filter) {
    FilterScopeProcessor processor = new FilterScopeProcessor(filter);
    PsiScopesUtil.resolveAndWalk(processor, reference, null, true);
    return processor.getResults().toArray();
  }

  public static boolean processDeclarationsInMethod(@NotNull final PsiMethod method,
                                                    @NotNull final PsiScopeProcessor processor,
                                                    @NotNull final ResolveState state,
                                                    final PsiElement lastParent,
                                                    @NotNull final PsiElement place) {
    final boolean fromBody = lastParent instanceof PsiCodeBlock;
    final PsiTypeParameterList typeParameterList = method.getTypeParameterList();
    final PsiParameterList parameterList = method.getParameterList();
    return processDeclarationsInMethodLike(method, processor, state, place, fromBody, typeParameterList, parameterList);
  }

  public static boolean processDeclarationsInLambda(@NotNull final PsiLambdaExpression lambda,
                                                    @NotNull final PsiScopeProcessor processor,
                                                    @NotNull final ResolveState state,
                                                    final PsiElement lastParent,
                                                    @NotNull final PsiElement place) {
    final boolean fromBody = lastParent != null && lastParent == lambda.getBody();
    final PsiParameterList parameterList = lambda.getParameterList();
    return processDeclarationsInMethodLike(lambda, processor, state, place, fromBody, null, parameterList);
  }

  private static boolean processDeclarationsInMethodLike(@NotNull final PsiElement element,
                                                         @NotNull final PsiScopeProcessor processor,
                                                         @NotNull final ResolveState state,
                                                         @NotNull final PsiElement place,
                                                         final boolean fromBody,
                                                         @Nullable final PsiTypeParameterList typeParameterList,
                                                         @NotNull final PsiParameterList parameterList) {
    processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, element);

    if (typeParameterList != null) {
      final ElementClassHint hint = processor.getHint(ElementClassHint.KEY);
      if (hint == null || hint.shouldProcess(ElementClassHint.DeclarationKind.CLASS)) {
        if (!typeParameterList.processDeclarations(processor, state, null, place)) return false;
      }
    }

    if (fromBody) {
      final PsiParameter[] parameters = parameterList.getParameters();
      for (PsiParameter parameter : parameters) {
        if (!processor.execute(parameter, state)) return false;
      }
    }

    return true;
  }

  public static boolean processDeclarationsInResourceList(@NotNull final PsiResourceList resourceList,
                                                          @NotNull final PsiScopeProcessor processor,
                                                          @NotNull final ResolveState state,
                                                          final PsiElement lastParent) {
    final ElementClassHint hint = processor.getHint(ElementClassHint.KEY);
    if (hint != null && !hint.shouldProcess(ElementClassHint.DeclarationKind.VARIABLE)) return true;

    final List<PsiResourceVariable> resources = resourceList.getResourceVariables();
    @SuppressWarnings({"SuspiciousMethodCalls"})
    final int lastIdx = lastParent instanceof PsiResourceVariable ? resources.indexOf(lastParent) : resources.size();
    for (int i = 0; i < lastIdx; i++) {
      if (!processor.execute(resources.get(i), state)) return false;
    }

    return true;
  }

  public static boolean hasTypeParameters(@NotNull PsiTypeParameterListOwner owner) {
    final PsiTypeParameterList typeParameterList = owner.getTypeParameterList();
    return typeParameterList != null && typeParameterList.getTypeParameters().length != 0;
  }

  @NotNull
  public static PsiType[] typesByReferenceParameterList(@NotNull PsiReferenceParameterList parameterList) {
    PsiTypeElement[] typeElements = parameterList.getTypeParameterElements();

    return typesByTypeElements(typeElements);
  }

  @NotNull
  public static PsiType[] typesByTypeElements(@NotNull PsiTypeElement[] typeElements) {
    PsiType[] types = new PsiType[typeElements.length];
    for (int i = 0; i < types.length; i++) {
      types[i] = typeElements[i].getType();
    }
    if (types.length == 1 && types[0] instanceof PsiDiamondType) {
      return ((PsiDiamondType)types[0]).resolveInferredTypes().getTypes();
    }
    return types;
  }

  public static PsiType getType(@NotNull PsiClassObjectAccessExpression classAccessExpression) {
    GlobalSearchScope resolveScope = classAccessExpression.getResolveScope();
    PsiManager manager = classAccessExpression.getManager();
    final PsiClass classClass = JavaPsiFacade.getInstance(manager.getProject()).findClass("java.lang.Class", resolveScope);
    if (classClass == null) {
      return new PsiClassReferenceType(new LightClassReference(manager, "Class", "java.lang.Class", resolveScope), null);
    }
    if (!PsiUtil.isLanguageLevel5OrHigher(classAccessExpression)) {
      //Raw java.lang.Class
      return JavaPsiFacade.getInstance(manager.getProject()).getElementFactory().createType(classClass);
    }

    PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
    PsiType operandType = classAccessExpression.getOperand().getType();
    if (operandType instanceof PsiPrimitiveType && !PsiType.NULL.equals(operandType)) {
      if (PsiType.VOID.equals(operandType)) {
        operandType = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory()
            .createTypeByFQClassName("java.lang.Void", classAccessExpression.getResolveScope());
      }
      else {
        operandType = ((PsiPrimitiveType)operandType).getBoxedType(classAccessExpression);
      }
    }
    final PsiTypeParameter[] typeParameters = classClass.getTypeParameters();
    if (typeParameters.length == 1) {
      substitutor = substitutor.put(typeParameters[0], operandType);
    }

    return new PsiImmediateClassType(classClass, substitutor);
  }

  @Nullable
  public static PsiAnnotation findAnnotation(@Nullable PsiAnnotationOwner annotationOwner, @NotNull String qualifiedName) {
    if (annotationOwner == null) return null;

    PsiAnnotation[] annotations = annotationOwner.getAnnotations();
    if (annotations.length == 0) return null;

    String shortName = StringUtil.getShortName(qualifiedName);
    for (PsiAnnotation annotation : annotations) {
      PsiJavaCodeReferenceElement referenceElement = annotation.getNameReferenceElement();
      if (referenceElement != null && shortName.equals(referenceElement.getReferenceName())) {
        if (qualifiedName.equals(annotation.getQualifiedName())) {
          return annotation;
        }
      }
    }

    return null;
  }

  @Nullable
  public static TargetType findApplicableTarget(@NotNull PsiAnnotation annotation, @NotNull TargetType... types) {
    if (types.length != 0) {
      PsiJavaCodeReferenceElement ref = annotation.getNameReferenceElement();
      if (ref != null) {
        PsiElement annotationType = ref.resolve();
        if (annotationType instanceof PsiClass) {
          return findApplicableTarget((PsiClass)annotationType, types);
        }
      }
    }

    return TargetType.UNKNOWN;
  }

  @Nullable
  public static TargetType findApplicableTarget(@NotNull PsiClass annotationType, @NotNull TargetType... types) {
    if (types.length != 0) {
      Set<TargetType> targets = getAnnotationTargets(annotationType);
      if (targets != null) {
        for (TargetType type : types) {
          if (type != TargetType.UNKNOWN && targets.contains(type)) {
            return type;
          }
        }
        return null;
      }
    }

    return TargetType.UNKNOWN;
  }

  // todo[r.sh] cache?
  @Nullable
  public static Set<TargetType> getAnnotationTargets(PsiClass annotationType) {
    if (!annotationType.isAnnotationType()) return null;
    PsiModifierList modifierList = annotationType.getModifierList();
    if (modifierList == null) return null;
    PsiAnnotation target = modifierList.findAnnotation(CommonClassNames.JAVA_LANG_ANNOTATION_TARGET);
    if (target == null) return DEFAULT_TARGETS;  // if omitted it is applicable to all but Java 8 TYPE_USE/TYPE_PARAMETERS targets

    PsiAnnotationMemberValue value = target.findAttributeValue(null);
    if (value instanceof PsiReference) {
      TargetType targetType = translateTargetRef((PsiReference)value);
      if (targetType != null) {
        return Collections.singleton(targetType);
      }
    }
    else if (value instanceof PsiArrayInitializerMemberValue) {
      Set <TargetType> targets = ContainerUtil.newHashSet();
      for (PsiAnnotationMemberValue initializer : ((PsiArrayInitializerMemberValue)value).getInitializers()) {
        if (initializer instanceof PsiReference) {
          TargetType targetType = translateTargetRef((PsiReference)initializer);
          if (targetType != null) {
            targets.add(targetType);
          }
        }
      }
      return targets;
    }

    return null;
  }

  @Nullable
  private static TargetType translateTargetRef(PsiReference reference) {
    PsiElement field = reference.resolve();
    if (field instanceof PsiEnumConstant) {
      String name = ((PsiEnumConstant)field).getName();
      try {
        return TargetType.valueOf(name);
      }
      catch (IllegalArgumentException e) {
        LOG.warn("Unknown target: " + name);
      }
    }
    return null;
  }

  @NotNull
  public static TargetType[] getTargetsForLocation(@Nullable PsiAnnotationOwner owner) {
    if (owner == null) {
      return TargetType.EMPTY_ARRAY;
    }

    if (owner instanceof PsiType || owner instanceof PsiTypeElement) {
      return TYPE_USE_TARGETS;
    }

    if (owner instanceof PsiTypeParameter) {
      return TYPE_PARAMETER_TARGETS;
    }

    if (owner instanceof PsiModifierList) {
      PsiElement element = ((PsiModifierList)owner).getParent();
      if (element instanceof PsiPackageStatement) {
        return PACKAGE_TARGETS;
      }
      if (element instanceof PsiClass) {
        if (((PsiClass)element).isAnnotationType()) {
          return ANNOTATION_TARGETS;
        }
        else {
          return TYPE_TARGETS;
        }
      }
      if (element instanceof PsiMethod) {
        if (((PsiMethod)element).isConstructor()) {
          return CONSTRUCTOR_TARGETS;
        }
        else {
          return METHOD_TARGETS;
        }
      }
      if (element instanceof PsiField) {
        return FIELD_TARGETS;
      }
      if (element instanceof PsiParameter) {
        return PARAMETER_TARGETS;
      }
      if (element instanceof PsiLocalVariable) {
        return LOCAL_VARIABLE_TARGETS;
      }
    }

    return TargetType.EMPTY_ARRAY;
  }

  @Nullable
  public static ASTNode findDocComment(@NotNull CompositeElement element) {
    TreeElement node = element.getFirstChildNode();
    while (node != null && (isWhitespaceOrComment(node) && !(node.getPsi() instanceof PsiDocComment))) {
      node = node.getTreeNext();
    }

    if (node != null && node.getElementType() == JavaDocElementType.DOC_COMMENT) {
      return node;
    }
    else {
      return null;
    }
  }

  public static PsiType normalizeWildcardTypeByPosition(@NotNull PsiType type, @NotNull PsiExpression expression) {
    LOG.assertTrue(expression.isValid());
    LOG.assertTrue(type.isValid());

    PsiExpression toplevel = expression;
    while (toplevel.getParent() instanceof PsiArrayAccessExpression &&
           ((PsiArrayAccessExpression)toplevel.getParent()).getArrayExpression() == toplevel) {
      toplevel = (PsiExpression)toplevel.getParent();
    }

    if (toplevel instanceof PsiArrayAccessExpression && !PsiUtil.isAccessedForWriting(toplevel)) {
      return PsiUtil.captureToplevelWildcards(type, expression);
    }

    final PsiType normalized = doNormalizeWildcardByPosition(type, expression, toplevel);
    LOG.assertTrue(normalized.isValid(), type);
    if (normalized instanceof PsiClassType && !PsiUtil.isAccessedForWriting(toplevel)) {
      return PsiUtil.captureToplevelWildcards(normalized, expression);
    }

    return normalized;
  }

  private static PsiType doNormalizeWildcardByPosition(final PsiType type, @NotNull PsiExpression expression, final PsiExpression toplevel) {
    if (type instanceof PsiCapturedWildcardType) {
      return doNormalizeWildcardByPosition(((PsiCapturedWildcardType)type).getWildcard(), expression, toplevel);
    }


    if (type instanceof PsiWildcardType) {
      final PsiWildcardType wildcardType = (PsiWildcardType)type;

      if (PsiUtil.isAccessedForWriting(toplevel)) {
        return wildcardType.isSuper() ? wildcardType.getBound() : PsiCapturedWildcardType.create(wildcardType, expression);
      }
      else {
        if (wildcardType.isExtends()) {
          return wildcardType.getBound();
        }
        else {
          return PsiType.getJavaLangObject(expression.getManager(), expression.getResolveScope());
        }
      }
    }
    else if (type instanceof PsiArrayType) {
      final PsiType componentType = ((PsiArrayType)type).getComponentType();
      final PsiType normalizedComponentType = doNormalizeWildcardByPosition(componentType, expression, toplevel);
      if (normalizedComponentType != componentType) {
        return normalizedComponentType.createArrayType();
      }
    }

    return type;
  }

  @NotNull
  public static SearchScope getMemberUseScope(@NotNull PsiMember member) {
    final GlobalSearchScope maximalUseScope = ResolveScopeManager.getElementUseScope(member);
    PsiFile file = member.getContainingFile();
    if (isInServerPage(file)) return maximalUseScope;

    PsiClass aClass = member.getContainingClass();
    if (aClass instanceof PsiAnonymousClass) {
      //member from anonymous class can be called from outside the class
      PsiElement methodCallExpr = PsiTreeUtil.getParentOfType(aClass, PsiMethodCallExpression.class);
      return new LocalSearchScope(methodCallExpr != null ? methodCallExpr : aClass);
    }

    if (member.hasModifierProperty(PsiModifier.PUBLIC)) {
      return maximalUseScope; // class use scope doesn't matter, since another very visible class can inherit from aClass
    }
    else if (member.hasModifierProperty(PsiModifier.PROTECTED)) {
      return maximalUseScope; // class use scope doesn't matter, since another very visible class can inherit from aClass
    }
    else if (member.hasModifierProperty(PsiModifier.PRIVATE)) {
      PsiClass topClass = PsiUtil.getTopLevelClass(member);
      return topClass != null ? new LocalSearchScope(topClass) : file != null ? new LocalSearchScope(file) : maximalUseScope;
    }
    else {
      if (file instanceof PsiJavaFile) {
        PsiPackage aPackage = JavaPsiFacade.getInstance(member.getProject()).findPackage(((PsiJavaFile)file).getPackageName());
        if (aPackage != null) {
          SearchScope scope = PackageScope.packageScope(aPackage, false);
          scope = scope.intersectWith(maximalUseScope);
          return scope;
        }
      }

      return maximalUseScope;
    }
  }

  public static boolean isInServerPage(@Nullable final PsiElement element) {
    return getServerPageFile(element) != null;
  }

  @Nullable public static ServerPageFile getServerPageFile(final PsiElement element) {
    final PsiFile psiFile = PsiUtilCore.getTemplateLanguageFile(element);
    return psiFile instanceof ServerPageFile ? (ServerPageFile)psiFile : null;
  }

  public static PsiElement setName(@NotNull PsiElement element, @NotNull String name) throws IncorrectOperationException {
    PsiManager manager = element.getManager();
    PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
    PsiIdentifier newNameIdentifier = factory.createIdentifier(name);
    return element.replace(newNameIdentifier);
  }

  public static boolean isDeprecatedByAnnotation(@NotNull PsiModifierListOwner owner) {
    PsiModifierList modifierList = owner.getModifierList();
    return modifierList != null && modifierList.findAnnotation("java.lang.Deprecated") != null;
  }

  public static boolean isDeprecatedByDocTag(@NotNull PsiDocCommentOwner owner) {
    PsiDocComment docComment = owner.getDocComment();
    return docComment != null && docComment.findTagByName("deprecated") != null;
  }

  @Nullable
  public static PsiAnnotationMemberValue setDeclaredAttributeValue(@NotNull PsiAnnotation psiAnnotation,
                                                                   @Nullable String attributeName,
                                                                   @Nullable PsiAnnotationMemberValue value,
                                                                   @NotNull PairFunction<Project, String, PsiAnnotation> annotationCreator) {
    final PsiAnnotationMemberValue existing = psiAnnotation.findDeclaredAttributeValue(attributeName);
    if (value == null) {
      if (existing == null) {
        return null;
      }
      existing.getParent().delete();
    } else {
      if (existing != null) {
        ((PsiNameValuePair)existing.getParent()).setValue(value);
      } else {
        final PsiNameValuePair[] attributes = psiAnnotation.getParameterList().getAttributes();
        if (attributes.length == 1 && attributes[0].getName() == null) {
          attributes[0].replace(createNameValuePair(attributes[0].getValue(), PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME + "=", annotationCreator));
        }

        boolean allowNoName = attributes.length == 0 && ("value".equals(attributeName) || null == attributeName);
        final String namePrefix;
        if (allowNoName) {
          namePrefix = "";
        } else {
          namePrefix = attributeName + "=";
        }
        psiAnnotation.getParameterList().addBefore(createNameValuePair(value, namePrefix, annotationCreator), null);
      }
    }
    return psiAnnotation.findDeclaredAttributeValue(attributeName);
  }

  private static PsiNameValuePair createNameValuePair(@NotNull PsiAnnotationMemberValue value,
                                                     @NotNull String namePrefix,
                                                     @NotNull PairFunction<Project, String, PsiAnnotation> annotationCreator) {
    return annotationCreator.fun(value.getProject(), "@A(" + namePrefix + value.getText() + ")").getParameterList().getAttributes()[0];
  }

  @Nullable
  public static ASTNode skipWhitespaceAndComments(final ASTNode node) {
    return skipWhitespaceCommentsAndTokens(node, TokenSet.EMPTY);
  }

  @Nullable
  public static ASTNode skipWhitespaceCommentsAndTokens(final ASTNode node, TokenSet alsoSkip) {
    ASTNode element = node;
    while (true) {
      if (element == null) return null;
      if (!isWhitespaceOrComment(element) && !alsoSkip.contains(element.getElementType())) break;
      element = element.getTreeNext();
    }
    return element;
  }

  public static boolean isWhitespaceOrComment(ASTNode element) {
    return element.getPsi() instanceof PsiWhiteSpace || element.getPsi() instanceof PsiComment;
  }

  @Nullable
  public static ASTNode skipWhitespaceAndCommentsBack(final ASTNode node) {
    if (node == null) return null;
    if (!isWhitespaceOrComment(node)) return node;

    ASTNode parent = node.getTreeParent();
    ASTNode prev = node;
    while (prev instanceof CompositeElement) {
      if (!isWhitespaceOrComment(prev)) return prev;
      prev = prev.getTreePrev();
    }
    if (prev == null) return null;
    ASTNode firstChildNode = parent.getFirstChildNode();
    ASTNode lastRelevant = null;
    while (firstChildNode != prev) {
      if (!isWhitespaceOrComment(firstChildNode)) lastRelevant = firstChildNode;
      firstChildNode = firstChildNode.getTreeNext();
    }
    return lastRelevant;
  }

  @Nullable
  public static ASTNode findStatementChild(CompositePsiElement statement) {
    if (DebugUtil.CHECK_INSIDE_ATOMIC_ACTION_ENABLED) {
      ApplicationManager.getApplication().assertReadAccessAllowed();
    }
    for (ASTNode element = statement.getFirstChildNode(); element != null; element = element.getTreeNext()) {
      if (element.getPsi() instanceof PsiStatement) return element;
    }
    return null;
  }

  public static PsiStatement[] getChildStatements(CompositeElement psiCodeBlock) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    // no lock is needed because all chameleons are expanded already
    int count = 0;
    for (ASTNode child1 = psiCodeBlock.getFirstChildNode(); child1 != null; child1 = child1.getTreeNext()) {
      if (child1.getPsi() instanceof PsiStatement) {
        count++;
      }
    }

    PsiStatement[] result = PsiStatement.ARRAY_FACTORY.create(count);
    if (count == 0) return result;
    int idx = 0;
    for (ASTNode child = psiCodeBlock.getFirstChildNode(); child != null && idx < count; child = child.getTreeNext()) {
      if (child.getPsi() instanceof PsiStatement) {
        PsiStatement element = (PsiStatement)child.getPsi();
        LOG.assertTrue(element != null, child);
        result[idx++] = element;
      }
    }
    return result;
  }

  public static boolean isVarArgs(@NotNull PsiMethod method) {
    PsiParameter[] parameters = method.getParameterList().getParameters();
    return parameters.length > 0 && parameters[parameters.length - 1].isVarArgs();
  }

  public static PsiElement handleMirror(PsiElement element) {
    return element instanceof PsiMirrorElement ? ((PsiMirrorElement)element).getPrototype() : element;
  }

  @Nullable
  public static PsiModifierList findNeighbourModifierList(@NotNull PsiJavaCodeReferenceElement ref) {
    PsiElement parent = PsiTreeUtil.skipParentsOfType(ref, PsiJavaCodeReferenceElement.class);
    if (parent instanceof PsiTypeElement) {
      PsiElement grandParent = parent.getParent();
      if (grandParent instanceof PsiModifierListOwner) {
        return ((PsiModifierListOwner)grandParent).getModifierList();
      }
    }

    return null;
  }

  @Nullable
  public static List<PsiAnnotation> getTypeUseAnnotations(@NotNull PsiModifierList modifierList) {
    SmartList<PsiAnnotation> result = null;

    for (PsiAnnotation annotation : modifierList.getAnnotations()) {
      if (findApplicableTarget(annotation, TargetType.TYPE_USE) == TargetType.TYPE_USE) {
        if (result == null) result = new SmartList<PsiAnnotation>();
        result.add(annotation);
      }
    }

    return result;
  }

  public static boolean isLeafElementOfType(@Nullable PsiElement element, IElementType type) {
    return element instanceof LeafElement && ((LeafElement)element).getElementType() == type;
  }

  public static boolean isLeafElementOfType(PsiElement element, TokenSet tokenSet) {
    return element instanceof LeafElement && tokenSet.contains(((LeafElement)element).getElementType());
  }

}

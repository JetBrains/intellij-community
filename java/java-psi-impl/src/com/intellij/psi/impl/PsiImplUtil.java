// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl;

import com.intellij.codeInsight.AnnotationTargetUtil;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.lang.ASTNode;
import com.intellij.lang.FileASTNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.impl.light.LightClassReference;
import com.intellij.psi.impl.light.LightJavaModule;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.impl.source.PsiImmediateClassType;
import com.intellij.psi.impl.source.resolve.ResolveCache;
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
import com.intellij.psi.util.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PairFunction;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.*;

import java.util.*;

public final class PsiImplUtil {
  private static final Logger LOG = Logger.getInstance(PsiImplUtil.class);
  private static final String JAVA_IO_IO = "java.io.IO";
  private static final String JAVA_BASE = "java.base";

  private PsiImplUtil() { }

  public static PsiMethod @NotNull [] getConstructors(@NotNull PsiClass aClass) {
    List<PsiMethod> result = null;
    for (PsiMethod method : aClass.getMethods()) {
      if (method.isConstructor() && method.getName().equals(aClass.getName())) {
        if (result == null) result = new SmartList<>();
        result.add(method);
      }
    }
    return result == null ? PsiMethod.EMPTY_ARRAY : result.toArray(PsiMethod.EMPTY_ARRAY);
  }

  public static @Nullable PsiAnnotationMemberValue findDeclaredAttributeValue(@NotNull PsiAnnotation annotation, @NonNls @Nullable String attributeName) {
    PsiNameValuePair attribute = AnnotationUtil.findDeclaredAttribute(annotation, attributeName);
    return attribute == null ? null : attribute.getValue();
  }

  public static @Nullable PsiAnnotationMemberValue findAttributeValue(@NotNull PsiAnnotation annotation, @Nullable @NonNls String attributeName) {
    final PsiAnnotationMemberValue value = findDeclaredAttributeValue(annotation, attributeName);
    if (value != null) return value;

    if (attributeName == null) attributeName = "value";
    final PsiJavaCodeReferenceElement referenceElement = annotation.getNameReferenceElement();
    if (referenceElement != null) {
      PsiElement resolved = referenceElement.resolve();
      if (resolved != null) {
        return findAttributeValue((PsiClass)resolved, attributeName);
      }
    }
    return null;
  }

  public static @Nullable PsiAnnotationMemberValue findAttributeValue(@NotNull PsiClass annotationClass, @Nullable @NonNls String attributeName) {
    PsiMethod[] methods = annotationClass.findMethodsByName(attributeName, false);
    for (PsiMethod method : methods) {
      if (PsiUtil.isAnnotationMethod(method)) {
        return ((PsiAnnotationMethod)method).getDefaultValue();
      }
    }
    return null;
  }

  public static PsiTypeParameter @NotNull [] getTypeParameters(@NotNull PsiTypeParameterListOwner owner) {
    final PsiTypeParameterList typeParameterList = owner.getTypeParameterList();
    if (typeParameterList != null) {
      return typeParameterList.getTypeParameters();
    }
    return PsiTypeParameter.EMPTY_ARRAY;
  }

  public static PsiJavaCodeReferenceElement @NotNull [] namesToPackageReferences(@NotNull PsiManager manager, String @NotNull [] names) {
    PsiJavaCodeReferenceElement[] refs = new PsiJavaCodeReferenceElement[names.length];
    for (int i = 0; i < names.length; i++) {
      String name = names[i];
      try {
        refs[i] = JavaPsiFacade.getElementFactory(manager.getProject()).createPackageReferenceElement(name);
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
    int i = ArrayUtil.indexOf(parameters, parameter);
    if (i != -1) return i;
    String name = parameter.getName();
    PsiParameter suspect = null;
    for (i = parameters.length - 1; i >= 0; i--) {
      PsiParameter paramInList = parameters[i];
      if (Objects.equals(name, paramInList.getName())) {
        suspect = paramInList;
        break;
      }
    }
    String message = parameter + ":" + parameter.getClass() + " not found among parameters: " + Arrays.asList(parameters) + "." +
                     " parameterList' parent: " + parameterList.getParent() + ";" +
                     " parameter.isValid()=" + parameter.isValid() + ";" +
                     " parameterList.isValid()= " + parameterList.isValid() + ";" +
                     " parameterList stub: " + (parameterList instanceof StubBasedPsiElement ? ((StubBasedPsiElement<?>)parameterList).getStub() : "---") + "; " +
                     " parameter stub: " + (parameter instanceof StubBasedPsiElement ? ((StubBasedPsiElement<?>)parameter).getStub() : "---") + ";" +
                     " suspect: " + suspect + " (index=" + i + "); " + (suspect==null?null:suspect.getClass()) +
                     " suspect stub: " + (suspect instanceof StubBasedPsiElement ? ((StubBasedPsiElement<?>)suspect).getStub() : suspect == null ? "-null-" : "---" + suspect.getClass()) + ";" +
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
    LOG.error(typeParameter + " in " + typeParameterList);
    return -1;
  }

  public static Object @NotNull [] getReferenceVariantsByFilter(@NotNull PsiJavaCodeReferenceElement reference, @NotNull ElementFilter filter) {
    FilterScopeProcessor processor = new FilterScopeProcessor(filter);
    PsiScopesUtil.resolveAndWalk(processor, reference, null, true);
    return processor.getResults().toArray();
  }

  public static boolean processDeclarationsInMethod(@NotNull PsiMethod method,
                                                    @NotNull PsiScopeProcessor processor,
                                                    @NotNull ResolveState state,
                                                    PsiElement lastParent,
                                                    @NotNull PsiElement place) {
    if (lastParent instanceof DummyHolder) lastParent = lastParent.getFirstChild();
    boolean fromBody = lastParent instanceof PsiCodeBlock;
    PsiTypeParameterList typeParameterList = method.getTypeParameterList();
    return processDeclarationsInMethodLike(method, processor, state, place, fromBody, typeParameterList);
  }

  public static boolean processDeclarationsInLambda(@NotNull PsiLambdaExpression lambda,
                                                    @NotNull PsiScopeProcessor processor,
                                                    @NotNull ResolveState state,
                                                    PsiElement lastParent,
                                                    @NotNull PsiElement place) {
    boolean fromBody;
    if (lastParent instanceof DummyHolder) {
      PsiElement firstChild = lastParent.getFirstChild();
      fromBody = firstChild instanceof PsiExpression || firstChild instanceof PsiCodeBlock;
    }
    else {
      fromBody = lastParent != null && lastParent == lambda.getBody();
    }
    return processDeclarationsInMethodLike(lambda, processor, state, place, fromBody, null);
  }

  private static boolean processDeclarationsInMethodLike(@NotNull PsiParameterListOwner element,
                                                         @NotNull PsiScopeProcessor processor,
                                                         @NotNull ResolveState state,
                                                         @NotNull PsiElement place,
                                                         boolean fromBody,
                                                         @Nullable PsiTypeParameterList typeParameterList) {
    processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, element);

    if (typeParameterList != null) {
      final ElementClassHint hint = processor.getHint(ElementClassHint.KEY);
      if (hint == null || hint.shouldProcess(ElementClassHint.DeclarationKind.CLASS)) {
        if (!typeParameterList.processDeclarations(processor, state, null, place)) return false;
      }
    }

    if (fromBody) {
      final PsiParameter[] parameters = element.getParameterList().getParameters();
      for (PsiParameter parameter : parameters) {
        if (parameter.isUnnamed()) continue;
        if (!processor.execute(parameter, state)) return false;
      }
    }

    return true;
  }

  public static boolean processDeclarationsInResourceList(@NotNull PsiResourceList resourceList,
                                                          @NotNull PsiScopeProcessor processor,
                                                          @NotNull ResolveState state,
                                                          PsiElement lastParent) {
    final ElementClassHint hint = processor.getHint(ElementClassHint.KEY);
    if (hint != null && !hint.shouldProcess(ElementClassHint.DeclarationKind.VARIABLE)) return true;

    for (PsiResourceListElement resource : resourceList) {
      if (resource == lastParent) break;
      if (resource instanceof PsiResourceVariable &&
          !((PsiResourceVariable)resource).isUnnamed() &&
          !processor.execute(resource, state)) return false;
    }

    return true;
  }

  public static boolean hasTypeParameters(@NotNull PsiTypeParameterListOwner owner) {
    final PsiTypeParameterList typeParameterList = owner.getTypeParameterList();
    return typeParameterList != null && typeParameterList.getTypeParameters().length != 0;
  }

  public static PsiType @NotNull [] typesByReferenceParameterList(@NotNull PsiReferenceParameterList parameterList) {
    PsiTypeElement[] typeElements = parameterList.getTypeParameterElements();

    return typesByTypeElements(typeElements);
  }

  public static PsiType @NotNull [] typesByTypeElements(PsiTypeElement @NotNull [] typeElements) {
    PsiType[] types = PsiType.createArray(typeElements.length);
    for (int i = 0; i < types.length; i++) {
      types[i] = typeElements[i].getType();
    }
    if (types.length == 1 && types[0] instanceof PsiDiamondType) {
      return ((PsiDiamondType)types[0]).resolveInferredTypes().getTypes();
    }
    return types;
  }

  public static @NotNull PsiType getType(@NotNull PsiClassObjectAccessExpression classAccessExpression) {
    GlobalSearchScope resolveScope = classAccessExpression.getResolveScope();
    PsiManager manager = classAccessExpression.getManager();
    final PsiClass classClass = JavaPsiFacade.getInstance(manager.getProject()).findClass("java.lang.Class", resolveScope);
    if (classClass == null) {
      return new PsiClassReferenceType(new LightClassReference(manager, "Class", "java.lang.Class", resolveScope), null);
    }
    if (!PsiUtil.isAvailable(JavaFeature.GENERICS, classAccessExpression)) {
      //Raw java.lang.Class
      return JavaPsiFacade.getElementFactory(manager.getProject()).createType(classClass);
    }

    PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
    PsiType operandType = classAccessExpression.getOperand().getType();
    if (operandType instanceof PsiPrimitiveType && !PsiTypes.nullType().equals(operandType)) {
      if (PsiTypes.voidType().equals(operandType)) {
        operandType = JavaPsiFacade.getElementFactory(manager.getProject())
            .createTypeByFQClassName("java.lang.Void", classAccessExpression.getResolveScope());
      }
      else {
        operandType = ((PsiPrimitiveType)operandType).getBoxedType(classAccessExpression);
      }
    }
    final PsiTypeParameter[] typeParameters = classClass.getTypeParameters();
    if (typeParameters.length == 1) {
      substitutor = substitutor.put(typeParameters[0], operandType instanceof PsiClassType ? ((PsiClassType)operandType).rawType() : operandType);
    }

    return new PsiImmediateClassType(classClass, substitutor);
  }

  public static @Nullable PsiAnnotation findAnnotation(@Nullable PsiAnnotationOwner annotationOwner, @NotNull String qualifiedName) {
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

  public static @Nullable ASTNode findDocComment(@NotNull CompositeElement element) {
    TreeElement node = element.getFirstChildNode();
    while (node != null && isWhitespaceOrComment(node) && !(node.getPsi() instanceof PsiDocComment)) {
      node = node.getTreeNext();
    }

    return node == null || node.getElementType() != JavaDocElementType.DOC_COMMENT ? null : node;
  }

  /**
   * @deprecated types should be proceed by the callers themselves
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static PsiType normalizeWildcardTypeByPosition(@NotNull PsiType type, @NotNull PsiExpression expression) {
    PsiUtil.ensureValidType(type);

    PsiExpression topLevel = expression;
    while (topLevel.getParent() instanceof PsiArrayAccessExpression &&
           ((PsiArrayAccessExpression)topLevel.getParent()).getArrayExpression() == topLevel) {
      topLevel = (PsiExpression)topLevel.getParent();
    }

    if (topLevel instanceof PsiArrayAccessExpression && !PsiUtil.isAccessedForWriting(topLevel)) {
      return PsiUtil.captureToplevelWildcards(type, expression);
    }

    final PsiType normalized = doNormalizeWildcardByPosition(type, expression, topLevel);
    LOG.assertTrue(normalized.isValid(), type);
    if (normalized instanceof PsiClassType && !PsiUtil.isAccessedForWriting(topLevel)) {
      return PsiUtil.captureToplevelWildcards(normalized, expression);
    }

    return normalized;
  }

  private static PsiType doNormalizeWildcardByPosition(PsiType type, @NotNull PsiExpression expression, @NotNull PsiExpression topLevel) {
    if (type instanceof PsiWildcardType) {
      final PsiWildcardType wildcardType = (PsiWildcardType)type;

      if (PsiUtil.isAccessedForWriting(topLevel)) {
        return wildcardType.isSuper() ? wildcardType.getBound() : PsiCapturedWildcardType.create(wildcardType, expression);
      }
      else {
        if (wildcardType.isExtends()) {
          return wildcardType.getBound();
        }
        return PsiType.getJavaLangObject(expression.getManager(), expression.getResolveScope());
      }
    }
    if (type instanceof PsiArrayType) {
      final PsiType componentType = ((PsiArrayType)type).getComponentType();
      final PsiType normalizedComponentType = doNormalizeWildcardByPosition(componentType, expression, topLevel);
      if (normalizedComponentType != componentType) {
        return normalizedComponentType.createArrayType();
      }
    }

    return type;
  }

  public static @NotNull SearchScope getMemberUseScope(@NotNull PsiMember member) {
    PsiFile file = member.getContainingFile();
    PsiElement topElement = file == null ? member : file;
    Project project = topElement.getProject();
    final GlobalSearchScope maximalUseScope = ResolveScopeManager.getInstance(project).getUseScope(topElement);
    if (isInServerPage(file)) return maximalUseScope;

    PsiClass aClass = member.getContainingClass();
    if (aClass instanceof PsiImplicitClass) {
      return new LocalSearchScope(aClass);
    }
    if (aClass instanceof PsiAnonymousClass && !(aClass instanceof PsiEnumConstantInitializer &&
                                                 member instanceof PsiMethod &&
                                                 member.hasModifierProperty(PsiModifier.PUBLIC) &&
                                                 ((PsiMethod)member).findSuperMethods().length > 0)) {
      //member from anonymous class can be called from outside the class
      PsiElement scope = PsiUtil.isLanguageLevel8OrHigher(aClass) ? PsiTreeUtil.getTopmostParentOfType(aClass, PsiStatement.class)
                                                                  : PsiTreeUtil.getParentOfType(aClass, PsiMethodCallExpression.class);
      if (scope instanceof PsiDeclarationStatement) {
        PsiElement[] elements = ((PsiDeclarationStatement)scope).getDeclaredElements();
        if (elements.length == 1 &&
            elements[0] instanceof PsiLocalVariable &&
            ((PsiLocalVariable)elements[0]).getTypeElement().isInferredType()) {
          // Inferred type: can be used in the surrounding code block as well
          scope = scope.getParent();
        }
      }
      return new LocalSearchScope(scope != null ? scope : aClass);
    }

    if (aClass != null) {
      PsiElement parent = aClass.getParent();
      while (parent instanceof PsiClass && !(parent instanceof PsiAnonymousClass) && !(parent instanceof PsiImplicitClass)) {
        parent = parent.getParent();
      }
      // members of local classes or of classes contained in anonymous or local classes have a small scope
      if (parent instanceof PsiAnonymousClass) return new LocalSearchScope(parent);
      if (parent instanceof PsiDeclarationStatement) return new LocalSearchScope(parent.getParent());
      if (parent instanceof PsiImplicitClass) return new LocalSearchScope(parent);
    }

    PsiModifierList modifierList = (member instanceof PsiRecordComponent && aClass != null) ?
                                   aClass.getModifierList() : member.getModifierList();
    int accessLevel = modifierList == null ? PsiUtil.ACCESS_LEVEL_PUBLIC : PsiUtil.getAccessLevel(modifierList);
    if (accessLevel == PsiUtil.ACCESS_LEVEL_PUBLIC ||
        accessLevel == PsiUtil.ACCESS_LEVEL_PROTECTED) {
      SearchScope classScope = getClassUseScopeIfApplicable(member, aClass, accessLevel);
      return (classScope != null) ? classScope : maximalUseScope;
    }
    if (accessLevel == PsiUtil.ACCESS_LEVEL_PRIVATE) {
      PsiClass topClass = PsiUtil.getTopLevelClass(member);
      return topClass != null ? new LocalSearchScope(topClass) : file == null ? maximalUseScope : new LocalSearchScope(file);
    }
    if (file instanceof PsiJavaFile) {
      PsiPackage aPackage = JavaPsiFacade.getInstance(project).findPackage(((PsiJavaFile)file).getPackageName());
      final SearchScope classScope = getClassUseScopeIfApplicable(member, aClass, accessLevel);
      if (classScope != null) return classScope;
      if (aPackage != null) {
        SearchScope scope = PackageScope.packageScope(aPackage, false);
        return scope.intersectWith(maximalUseScope);
      }
    }
    return maximalUseScope;
  }

  private static SearchScope getClassUseScopeIfApplicable(PsiMember member, PsiClass aClass, int accessLevel) {
    if (aClass == null) return null;
    final PsiModifierList classModifierList = aClass.getModifierList();
    if (classModifierList == null) return null;
    if (classModifierList.hasModifierProperty(PsiModifier.FINAL) ||
        member instanceof PsiMethod && ((PsiMethod)member).isConstructor()) {
      // constructors and members of final classes cannot be accessed via a subclass so their use scope can't be wider than their class's
      return (PsiUtil.getAccessLevel(classModifierList) < accessLevel) ? aClass.getUseScope() : null;
    }
    else if (!mayHaveScopeWideningSubclass(aClass)) {
      return aClass.getUseScope();
    }
    // class use scope doesn't matter, since another very visible class can inherit from aClass
    return null;
  }

  private static boolean mayHaveScopeWideningSubclass(PsiClass aClass) {
    return CachedValuesManager.getCachedValue(aClass, () ->
      CachedValueProvider.Result.create(mayHaveScopeWideningSubclass(aClass, new HashSet<>()),
                                        PsiModificationTracker.MODIFICATION_COUNT));
  }

  private static boolean mayHaveScopeWideningSubclass(PsiClass aClass, Set<PsiClass> visited) {
    if (!aClass.hasModifierProperty(PsiModifier.PRIVATE)) return true;
    if (aClass instanceof PsiCompiledElement) return true; // don't check library code
    if (!visited.add(aClass)) return true; // prevent infinite recursion on broken code
    class LocalInheritorVisitor extends JavaRecursiveElementWalkingVisitor {
      private final Set<PsiClass> subclasses = new HashSet<>();

      @Override
      public void visitReferenceElement(@NotNull PsiJavaCodeReferenceElement reference) {
        final PsiElement parent = reference.getParent();
        if (!(parent instanceof PsiReferenceList)) return;
        final PsiElement grandParent = parent.getParent();
        if (!(grandParent instanceof PsiClass)) return;
        if (reference.isReferenceTo(aClass)) {
          subclasses.add((PsiClass)grandParent);
        }
      }

      @Override
      public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {}

      public boolean isExtended() {
        return !subclasses.isEmpty();
      }
    }
    PsiClass context = PsiUtil.getTopLevelClass(aClass);
    if (context == null) return false;
    final LocalInheritorVisitor visitor = new LocalInheritorVisitor();
    context.accept(visitor);
    return visitor.isExtended() && ContainerUtil.exists(visitor.subclasses, subclass -> mayHaveScopeWideningSubclass(subclass, visited));
  }

  public static boolean isInServerPage(@Nullable PsiElement element) {
    return getServerPageFile(element) != null;
  }

  private static @Nullable ServerPageFile getServerPageFile(PsiElement element) {
    final PsiFile psiFile = PsiUtilCore.getTemplateLanguageFile(element);
    return psiFile instanceof ServerPageFile ? (ServerPageFile)psiFile : null;
  }

  public static PsiElement setName(@NotNull PsiElement element, @NotNull String name) throws IncorrectOperationException {
    PsiManager manager = element.getManager();
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(manager.getProject());
    PsiIdentifier newNameIdentifier = factory.createIdentifier(name);
    return element.replace(newNameIdentifier);
  }

  public static boolean isDeprecatedByAnnotation(@NotNull PsiModifierListOwner owner) {
    return AnnotationUtil.findAnnotation(owner, CommonClassNames.JAVA_LANG_DEPRECATED) != null;
  }

  public static boolean isDeprecatedByDocTag(@NotNull PsiJavaDocumentedElement owner) {
    PsiDocComment docComment = owner.getDocComment();
    return docComment != null && docComment.findTagByName("deprecated") != null;
  }

  /**
   * Checks if the given PSI element is deprecated with annotation or JavaDoc tag.
   * <br>
   * It is suitable for elements other than {@link PsiDocCommentOwner}.
   * <p>
   *   Prefer specifying context for more precise check using JavaDeprecationUtils#isDeprecated.
   * </p>
   */
  public static boolean isDeprecated(@NotNull PsiElement psiElement) {
    if (psiElement instanceof PsiDocCommentOwner) {
      return ((PsiDocCommentOwner)psiElement).isDeprecated();
    }
    if (psiElement instanceof PsiModifierListOwner && isDeprecatedByAnnotation((PsiModifierListOwner) psiElement)) {
      return true;
    }
    if (psiElement instanceof PsiJavaDocumentedElement) {
      return isDeprecatedByDocTag((PsiJavaDocumentedElement)psiElement);
    }
    return false;
  }


  public static @Nullable PsiJavaDocumentedElement findDocCommentOwner(@NotNull PsiDocComment comment) {
    PsiElement parent = comment.getParent();
    if (parent instanceof PsiJavaDocumentedElement) {
      PsiJavaDocumentedElement owner = (PsiJavaDocumentedElement)parent;
      if (owner.getDocComment() == comment) {
        return owner;
      }
    }
    return null;
  }

  public static @Nullable PsiAnnotationMemberValue setDeclaredAttributeValue(@NotNull PsiAnnotation psiAnnotation,
                                                                             @Nullable String attributeName,
                                                                             @Nullable PsiAnnotationMemberValue value,
                                                                             @NotNull PairFunction<? super Project, ? super String, ? extends PsiAnnotation> annotationCreator) {
    PsiAnnotationMemberValue existing = psiAnnotation.findDeclaredAttributeValue(attributeName);
    if (value == null) {
      if (existing == null) {
        return null;
      }
      existing.getParent().delete();
    }
    else {
      if (existing != null) {
        ((PsiNameValuePair)existing.getParent()).setValue(value);
      }
      else {
        PsiNameValuePair[] attributes = psiAnnotation.getParameterList().getAttributes();
        if (attributes.length == 1) {
          PsiNameValuePair attribute = attributes[0];
          if (attribute.getName() == null) {
            PsiAnnotationMemberValue defValue = attribute.getValue();
            assert defValue != null : attribute;
            attribute.replace(createNameValuePair(defValue, PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME + "=", annotationCreator));
          }
        }

        boolean allowNoName = attributes.length == 0 && ("value".equals(attributeName) || null == attributeName);
        final String namePrefix = allowNoName ? "" : attributeName + "=";
        psiAnnotation.getParameterList().addBefore(createNameValuePair(value, namePrefix, annotationCreator), null);
      }
    }
    return psiAnnotation.findDeclaredAttributeValue(attributeName);
  }

  private static PsiNameValuePair createNameValuePair(@NotNull PsiAnnotationMemberValue value,
                                                     @NotNull String namePrefix,
                                                     @NotNull PairFunction<? super Project, ? super String, ? extends PsiAnnotation> annotationCreator) {
    return annotationCreator.fun(value.getProject(), "@A(" + namePrefix + value.getText() + ")").getParameterList().getAttributes()[0];
  }

  public static @Nullable ASTNode skipWhitespaceAndComments(ASTNode node) {
    return TreeUtil.skipWhitespaceAndComments(node, true);
  }

  public static @Nullable ASTNode skipWhitespaceCommentsAndTokens(ASTNode node, @NotNull TokenSet alsoSkip) {
    return TreeUtil.skipWhitespaceCommentsAndTokens(node, alsoSkip, true);
  }

  public static boolean isWhitespaceOrComment(ASTNode element) {
    return TreeUtil.isWhitespaceOrComment(element);
  }

  public static @Nullable ASTNode skipWhitespaceAndCommentsBack(ASTNode node) {
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

  public static @Nullable ASTNode findStatementChild(@NotNull CompositePsiElement statement) {
    if (DebugUtil.CHECK_INSIDE_ATOMIC_ACTION_ENABLED) {
      ApplicationManager.getApplication().assertReadAccessAllowed();
    }
    for (ASTNode element = statement.getFirstChildNode(); element != null; element = element.getTreeNext()) {
      if (element.getPsi() instanceof PsiStatement) return element;
    }
    return null;
  }

  public static PsiStatement @NotNull [] getChildStatements(@NotNull CompositeElement psiCodeBlock) {
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
      PsiElement element = child.getPsi();
      if (element instanceof PsiStatement) {
        result[idx++] = (PsiStatement)element;
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

  public static @Nullable PsiModifierList findNeighbourModifierList(@NotNull PsiJavaCodeReferenceElement ref) {
    PsiElement parent = PsiTreeUtil.skipParentsOfType(ref, PsiJavaCodeReferenceElement.class);
    if (parent instanceof PsiTypeElement) {
      PsiElement grandParent = parent.getParent();
      if (grandParent instanceof PsiModifierListOwner) {
        return ((PsiModifierListOwner)grandParent).getModifierList();
      }
    }

    return null;
  }

  public static boolean isTypeAnnotation(@Nullable PsiElement element) {
    return element instanceof PsiAnnotation && AnnotationTargetUtil.isTypeAnnotation((PsiAnnotation)element);
  }

  public static void collectTypeUseAnnotations(@NotNull PsiModifierList modifierList, @NotNull List<? super PsiAnnotation> annotations) {
    AnnotationTargetUtil.collectStrictlyTypeUseAnnotations(modifierList, annotations);
  }

  private static final Key<Boolean> TYPE_ANNO_MARK = Key.create("type.annotation.mark");

  public static void markTypeAnnotations(@NotNull PsiTypeElement typeElement) {
    PsiElement left = PsiTreeUtil.skipSiblingsBackward(typeElement, PsiComment.class, PsiWhiteSpace.class, PsiTypeParameterList.class);
    if (left instanceof PsiModifierList) {
      for (PsiAnnotation annotation : ((PsiModifierList)left).getAnnotations()) {
        if (AnnotationTargetUtil.isTypeAnnotation(annotation)) {
          annotation.putUserData(TYPE_ANNO_MARK, Boolean.TRUE);
        }
      }
    }
  }

  public static void deleteTypeAnnotations(@NotNull PsiTypeElement typeElement) {
    PsiElement left = PsiTreeUtil.skipSiblingsBackward(typeElement, PsiComment.class, PsiWhiteSpace.class, PsiTypeParameterList.class);
    if (left instanceof PsiModifierList) {
      for (PsiAnnotation annotation : ((PsiModifierList)left).getAnnotations()) {
        if (TYPE_ANNO_MARK.get(annotation) == Boolean.TRUE) {
          annotation.delete();
        }
      }
    }
  }

  public static @Nullable PsiLoopStatement findEnclosingLoop(@NotNull PsiElement start) {
    for (PsiElement e = start; !isCodeBoundary(e); e = e.getParent()) {
      if (e instanceof PsiLoopStatement) return (PsiLoopStatement)e;
    }
    return null;
  }

  public static @Nullable PsiStatement findEnclosingSwitchOrLoop(@NotNull PsiElement start) {
    for (PsiElement e = start; !isCodeBoundary(e); e = e.getParent()) {
      if (e instanceof PsiSwitchStatement || e instanceof PsiLoopStatement) return (PsiStatement)e;
    }
    return null;
  }

  public static @Nullable PsiSwitchExpression findEnclosingSwitchExpression(@NotNull PsiElement start) {
    for (PsiElement e = start; !isCodeBoundary(e); e = e.getParent()) {
      if (e instanceof PsiSwitchExpression) return (PsiSwitchExpression)e;
    }
    return null;
  }

  public static @Nullable PsiLabeledStatement findEnclosingLabeledStatement(@NotNull PsiElement start, @NotNull String label) {
    for (PsiElement e = start; !isCodeBoundary(e); e = e.getParent()) {
      if (e instanceof PsiLabeledStatement && label.equals(((PsiLabeledStatement)e).getName())) return (PsiLabeledStatement)e;
    }
    return null;
  }

  public static @NotNull @Unmodifiable List<String> findAllEnclosingLabels(@NotNull PsiElement start) {
    List<String> result = new SmartList<>();
    for (PsiElement context = start; !isCodeBoundary(context); context = context.getContext()) {
      if (context instanceof PsiLabeledStatement) {
        result.add(((PsiLabeledStatement)context).getName());
      }
    }
    return result;
  }

  private static boolean isCodeBoundary(@Nullable PsiElement e) {
    return e == null || e instanceof PsiMethod || e instanceof PsiClassInitializer || e instanceof PsiLambdaExpression;
  }

  /**
   * Returns enclosing label statement for given case label element
   *
   * @param labelElement case label element
   * @return enclosing label statement or null if {@param labelElement} is an expression but not a label statement expression
   */
  public static @Nullable PsiSwitchLabelStatementBase getSwitchLabel(@NotNull PsiCaseLabelElement labelElement) {
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(labelElement.getParent());
    if (parent instanceof PsiCaseLabelElementList) {
      PsiElement grand = parent.getParent();
      if (grand instanceof PsiSwitchLabelStatementBase) {
        return (PsiSwitchLabelStatementBase)grand;
      }
    }
    return null;
  }

  public static boolean isLeafElementOfType(@Nullable PsiElement element, @NotNull IElementType type) {
    return element instanceof LeafElement && ((LeafElement)element).getElementType() == type;
  }

  public static boolean isLeafElementOfType(PsiElement element, @NotNull TokenSet tokenSet) {
    return element instanceof LeafElement && tokenSet.contains(((LeafElement)element).getElementType());
  }

  public static PsiType buildTypeFromTypeString(@NotNull String typeName, @NotNull PsiElement context, @NotNull PsiFile psiFile) {
    final PsiManager psiManager = psiFile.getManager();

    if (typeName.indexOf('<') != -1 || typeName.indexOf('[') != -1 || typeName.indexOf('.') == -1) {
      try {
        return JavaPsiFacade.getElementFactory(psiManager.getProject()).createTypeFromText(typeName, context);
      }
      catch(Exception ignored) { } // invalid syntax will produce unresolved class type
    }

    PsiClass aClass = JavaPsiFacade.getInstance(psiManager.getProject()).findClass(typeName, context.getResolveScope());

    PsiType resultType;
    if (aClass == null) {
      final LightClassReference ref = new LightClassReference(
        psiManager,
        PsiNameHelper.getShortClassName(typeName),
        typeName,
        PsiSubstitutor.EMPTY,
        psiFile
      );
      resultType = new PsiClassReferenceType(ref, null);
    } else {
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(psiManager.getProject());
      PsiSubstitutor substitutor = factory.createRawSubstitutor(aClass);
      resultType = factory.createType(aClass, substitutor);
    }

    return resultType;
  }

  public static <T extends PsiJavaCodeReferenceElement> JavaResolveResult @NotNull [] multiResolveImpl(@NotNull T element,
                                                                                                       boolean incompleteCode,
                                                                                                       @NotNull ResolveCache.PolyVariantContextResolver<? super T> resolver) {
    FileASTNode fileElement = SharedImplUtil.findFileElement(element.getNode());
    if (fileElement == null) {
      PsiUtilCore.ensureValid(element);
      LOG.error("fileElement == null!");
      return JavaResolveResult.EMPTY_ARRAY;
    }
    PsiFile psiFile = SharedImplUtil.getContainingFile(fileElement);
    return multiResolveImpl(element, psiFile, incompleteCode, resolver);
  }

  public static <T extends PsiJavaCodeReferenceElement> @NotNull JavaResolveResult @NotNull [] multiResolveImpl(
    @NotNull T element, PsiFile psiFile, boolean incompleteCode, ResolveCache.@NotNull PolyVariantContextResolver<? super T> resolver) {
    PsiManager manager = psiFile == null ? null : psiFile.getManager();
    if (manager == null) {
      PsiUtilCore.ensureValid(element);
      LOG.error("getManager() == null!");
      return JavaResolveResult.EMPTY_ARRAY;
    }
    boolean valid = psiFile.isValid();
    if (!valid) {
      PsiUtilCore.ensureValid(element);
      LOG.error("psiFile.isValid() == false!");
      return JavaResolveResult.EMPTY_ARRAY;
    }
    if (element instanceof PsiMethodReferenceExpression) {
      // method refs: do not cache results during parent conflict resolving, acceptable checks, etc
      if (ThreadLocalTypes.hasBindingFor(element)) {
        return (JavaResolveResult[])resolver.resolve(element, psiFile, incompleteCode);
      }
    }

    return multiResolveImpl(manager.getProject(), psiFile, element, incompleteCode, resolver);
  }

  public static <T extends PsiJavaCodeReferenceElement> JavaResolveResult @NotNull [] multiResolveImpl(@NotNull Project project,
                                                                                                       @NotNull PsiFile psiFile,
                                                                                                       @NotNull T element,
                                                                                                       boolean incompleteCode,
                                                                                                       @NotNull ResolveCache.PolyVariantContextResolver<? super T> resolver) {
    ResolveResult[] results = ResolveCache.getInstance(project).resolveWithCaching(element, resolver, true, incompleteCode, psiFile);
    return results.length == 0 ? JavaResolveResult.EMPTY_ARRAY : (JavaResolveResult[])results;
  }

  public static @NotNull VirtualFile getModuleVirtualFile(@NotNull PsiJavaModule module) {
    if (module instanceof LightJavaModule) {
      return ((LightJavaModule)module).getRootVirtualFile();
    }
    else {
      VirtualFile file = PsiUtilCore.getVirtualFile(module);
      if (file == null) {
        throw new IllegalArgumentException("Module '" + module + "' lost its VF; file=" + module.getContainingFile() + "; valid=" + module.isValid());
      }
      return file;
    }
  }

  /**
   * Retrieves the implicit imports for the given file (except packages).
   *
   * @param file the file for which to retrieve implicit static imports
   * @return an array of static members representing the implicit static imports
   */
  @ApiStatus.Experimental
  public static @NotNull ImplicitlyImportedElement @NotNull[] getImplicitImports(@NotNull PsiFile file) {
    List<ImplicitlyImportedElement> implicitImports = new ArrayList<>();
    Project project = file.getProject();
    // java.lang.StringTemplate.STR
    if (PsiUtil.isAvailable(JavaFeature.STRING_TEMPLATES, file)) {
      implicitImports.add(ImplicitlyImportedStaticMember.create(project, CommonClassNames.JAVA_LANG_STRING_TEMPLATE, "STR"));
    }

    // java.io.IO.* for implicit classes
    if (PsiUtil.isAvailable(JavaFeature.IMPLICIT_IMPORT_IN_IMPLICIT_CLASSES, file) && file instanceof PsiJavaFile) {
      PsiClass[] classes = ((PsiJavaFile)file).getClasses();
      if (classes.length == 1 && classes[0] instanceof PsiImplicitClass) {
        implicitImports.add(ImplicitlyImportedStaticMember.create(project, JAVA_IO_IO, "*"));
      }
    }

    // import module java.base; for implicit classes
    if (PsiUtil.isAvailable(JavaFeature.IMPLICIT_IMPORT_IN_IMPLICIT_CLASSES, file) &&
        PsiUtil.isAvailable(JavaFeature.MODULE_IMPORT_DECLARATIONS, file) &&
        file instanceof PsiJavaFile) {
      PsiClass[] classes = ((PsiJavaFile)file).getClasses();
      if (classes.length == 1 && classes[0] instanceof PsiImplicitClass) {
        implicitImports.add(ImplicitlyImportedModule.create(project, JAVA_BASE));
      }
    }

    return implicitImports.toArray(ImplicitlyImportedElement.EMPTY_ARRAY);
  }

  /**
   * Retrieves the corresponding original element for the given PsiElement by looking for a corresponding child within the
   * parent's original element children.
   *
   * @param <T> the type of the PsiElement to find, which is common for a compiled and non-compiled element (e.g., {@link PsiTypeElement})
   * @param element the PsiElement to find the corresponding original element for
   * @param cls the class type of the PsiElement
   * @return the corresponding original element of the specified type if found, otherwise returns the input element
   */
  public static <T extends PsiElement> @NotNull T getCorrespondingOriginalElementOfType(@NotNull T element, @NotNull Class<T> cls) {
    PsiElement parent = element.getParent();
    if (parent != null) {
      PsiElement original = parent.getOriginalElement();
      if (original != parent) {
        long index = StreamEx.of(parent.getChildren()).select(cls).indexOf(element).orElse(-1);
        if (index != -1) {
          return StreamEx.of(original.getChildren()).select(cls).skip(index).findFirst().orElse(element);
        }
      }
    }
    return element;
  }
}

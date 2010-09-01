/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.impl.light.LightClassReference;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.impl.source.PsiImmediateClassType;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.processor.FilterScopeProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.PackageScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PairFunction;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public class PsiImplUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.PsiImplUtil");

  private PsiImplUtil() {
  }

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
      if (Comparing.equal(name, attributeName) || attributeName == null && name.equals(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME)) {
        return attribute.getValue();
      }
    }
    return null;
  }

  @Nullable
  public static PsiAnnotationMemberValue findAttributeValue(@NotNull PsiAnnotation annotation, @NonNls String attributeName) {
    final PsiAnnotationMemberValue value = findDeclaredAttributeValue(annotation, attributeName);
    if (value != null) return value;

    if (attributeName == null) attributeName = "value";
    final PsiJavaCodeReferenceElement referenceElement = annotation.getNameReferenceElement();
    if (referenceElement != null) {
      PsiElement resolved = referenceElement.resolve();
      if (resolved != null) {
        PsiMethod[] methods = ((PsiClass)resolved).getMethods();
        for (PsiMethod method : methods) {
          if (method instanceof PsiAnnotationMethod && Comparing.equal(method.getName(), attributeName)) {
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
    PsiParameter[] parameters = parameterList.getParameters();
    for (int i = 0; i < parameters.length; i++) {
      if (parameter.equals(parameters[i])) return i;
    }
    LOG.error("Parameter " + parameter + " not found among parameters: " + Arrays.asList(parameters)+
              ". parameterList' parent: "+parameterList.getParent()+"; parameter.getParent()==paramList: "+(parameter.getParent()==parameterList)
              +"; "+parameterList.getClass() + "; parameter.isValid()="+parameter.isValid()+"; parameterList.isValid()= "+parameterList.isValid());
    return -1;
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

  public static boolean processDeclarationsInMethod(PsiMethod method,
                                                    @NotNull PsiScopeProcessor processor,
                                                    ResolveState state,
                                                    PsiElement lastParent,
                                                    PsiElement place) {
    final ElementClassHint hint = processor.getHint(ElementClassHint.KEY);
    processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, method);
    if (hint == null || hint.shouldProcess(ElementClassHint.DeclaractionKind.CLASS)) {
      final PsiTypeParameterList list = method.getTypeParameterList();
      if (list != null && !list.processDeclarations(processor, state, null, place)) return false;
    }
    if (lastParent instanceof PsiCodeBlock) {
      final PsiParameter[] parameters = method.getParameterList().getParameters();
      for (PsiParameter parameter : parameters) {
        if (!processor.execute(parameter, state)) return false;
      }
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
      return ((PsiDiamondType)types[0]).getInferredTypes();
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

  public static PsiAnnotation findAnnotation(@NotNull PsiAnnotationOwner modifierList, @NotNull String qualifiedName) {
    final String shortName = StringUtil.getShortName(qualifiedName);
    PsiAnnotation[] annotations = modifierList.getAnnotations();
    for (PsiAnnotation annotation : annotations) {
      final PsiJavaCodeReferenceElement referenceElement = annotation.getNameReferenceElement();
      if (referenceElement != null && shortName.equals(referenceElement.getReferenceName())) {
        if (qualifiedName.equals(annotation.getQualifiedName())) return annotation;
      }
    }

    return null;
  }

  @Nullable
  public static ASTNode findDocComment(@NotNull CompositeElement element) {
    TreeElement node = element.getFirstChildNode();
    while (node != null &&
           (ElementType.WHITE_SPACE_BIT_SET.contains(node.getElementType()) ||
            node.getElementType() == JavaTokenType.C_STYLE_COMMENT ||
            node.getElementType() == JavaTokenType.END_OF_LINE_COMMENT)) {
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
    PsiExpression toplevel = expression;
    while (toplevel.getParent() instanceof PsiArrayAccessExpression &&
           ((PsiArrayAccessExpression)toplevel.getParent()).getArrayExpression() == toplevel) {
      toplevel = (PsiExpression)toplevel.getParent();
    }

    final PsiType normalized = doNormalizeWildcardByPosition(type, expression, toplevel);
    if (normalized instanceof PsiClassType && !PsiUtil.isAccessedForWriting(toplevel)) {
      return PsiUtil.captureToplevelWildcards(normalized, expression);
    }

    return normalized;
  }

  private static PsiType doNormalizeWildcardByPosition(final PsiType type, final PsiExpression expression, final PsiExpression toplevel) {
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
    final PsiManagerEx psiManager = (PsiManagerEx)member.getManager();
    final GlobalSearchScope maximalUseScope = psiManager.getFileManager().getUseScope(member);
    PsiFile file = member.getContainingFile();
    if (JspPsiUtil.isInJspFile(file)) return maximalUseScope;

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
      return topClass != null ? new LocalSearchScope(topClass) : new LocalSearchScope(file);
    }
    else {
      if (file instanceof PsiJavaFile) {
        PsiPackage aPackage = JavaPsiFacade.getInstance(psiManager.getProject()).findPackage(((PsiJavaFile)file).getPackageName());
        if (aPackage != null) {
          SearchScope scope = PackageScope.packageScope(aPackage, false);
          scope = scope.intersectWith(maximalUseScope);
          return scope;
        }
      }

      return maximalUseScope;
    }
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
}

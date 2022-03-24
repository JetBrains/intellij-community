// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source;

import com.intellij.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments;
import com.intellij.openapi.util.RecursionGuard;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.*;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.PsiJavaParserFacadeImpl;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.List;

public class PsiTypeElementImpl extends CompositePsiElement implements PsiTypeElement {
  public PsiTypeElementImpl() {
    this(JavaElementType.TYPE);
  }

  PsiTypeElementImpl(@NotNull IElementType type) {
    super(type);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitTypeElement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public @NotNull PsiType getType() {
    return CachedValuesManager.getProjectPsiDependentCache(this, __ -> calculateType());
  }

  private @NotNull PsiType calculateType() {
    PsiType inferredType = PsiAugmentProvider.getInferredType(this);
    if (inferredType != null) {
      return inferredType;
    }

    PsiType type = null;
    boolean ellipsis = false;
    List<PsiAnnotation> annotations = new SmartList<>();
    List<TypeAnnotationProvider> arrayComponentAnnotations = new SmartList<>();

    PsiElement parent = getParent();
    for (PsiElement child = getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof PsiComment || child instanceof PsiWhiteSpace) continue;

      if (child instanceof PsiAnnotation) {
        annotations.add((PsiAnnotation)child);
      }
      else if (child instanceof PsiTypeElement) {
        assert type == null : this;
        if (child instanceof PsiDiamondTypeElementImpl) {
          type = new PsiDiamondTypeImpl(getManager(), this);
          break;
        }
        else {
          type = ((PsiTypeElement)child).getType();
        }
      }
      else if (PsiUtil.isJavaToken(child, ElementType.PRIMITIVE_TYPE_BIT_SET)) {
        assert type == null : this;
        String text = child.getText();
        type = annotations.isEmpty() ? PsiJavaParserFacadeImpl.getPrimitiveType(text) : new PsiPrimitiveType(text, createProvider(annotations));
      }
      else if (PsiUtil.isJavaToken(child, JavaTokenType.VAR_KEYWORD)) {
        assert type == null : this;
        type = inferVarType(parent);
      }
      else if (child instanceof PsiJavaCodeReferenceElement) {
        assert type == null : this;
        type = new PsiClassReferenceType(getReferenceComputable((PsiJavaCodeReferenceElement)child), null, createProvider(annotations));
      }
      else if (PsiUtil.isJavaToken(child, JavaTokenType.LBRACKET)) {
        assert type != null : this;
        arrayComponentAnnotations.add(createProvider(annotations));
        annotations = new SmartList<>();
      }
      else if (PsiUtil.isJavaToken(child, JavaTokenType.ELLIPSIS)) {
        assert type != null : this;
        arrayComponentAnnotations.add(createProvider(annotations));
        annotations = new SmartList<>();
        ellipsis = true;
      }

      if (PsiUtil.isJavaToken(child, JavaTokenType.QUEST) ||
          child instanceof ASTNode && ((ASTNode)child).getElementType() == JavaElementType.DUMMY_ELEMENT && "any".equals(child.getText())) {
        assert type == null : this;
        PsiElement boundKind = PsiTreeUtil.skipWhitespacesAndCommentsForward(child);
        PsiElement boundType = PsiTreeUtil.skipWhitespacesAndCommentsForward(boundKind);
        if (PsiUtil.isJavaToken(boundKind, JavaTokenType.EXTENDS_KEYWORD) && boundType instanceof PsiTypeElement) {
          type = PsiWildcardType.createExtends(getManager(), ((PsiTypeElement)boundType).getType());
        }
        else if (PsiUtil.isJavaToken(boundKind, JavaTokenType.SUPER_KEYWORD) && boundType instanceof PsiTypeElement) {
          type = PsiWildcardType.createSuper(getManager(), ((PsiTypeElement)boundType).getType());
        }
        else {
          type = PsiWildcardType.createUnbounded(getManager());
        }
        type = type.annotate(createProvider(annotations));
        break;
      }

      if (PsiUtil.isJavaToken(child, JavaTokenType.AND)) {
        List<PsiType> types = collectTypes();
        assert !types.isEmpty() : this;
        type = PsiIntersectionType.createIntersection(false, types.toArray(PsiType.createArray(types.size())));
        break;
      }

      if (PsiUtil.isJavaToken(child, JavaTokenType.OR)) {
        List<PsiType> types = collectTypes();
        assert !types.isEmpty() : this;
        type = PsiDisjunctionType.createDisjunction(types, getManager());
        break;
      }
    }

    if (type == null) return PsiType.NULL;

    if (!arrayComponentAnnotations.isEmpty()) {
      type = createArray(type, arrayComponentAnnotations, ellipsis);
    }

    if (parent instanceof PsiModifierListOwner) {
      type = JavaSharedImplUtil.applyAnnotations(type, ((PsiModifierListOwner)parent).getModifierList());
    }

    return type;
  }
  
  private static PsiType createArray(PsiType elementType, List<TypeAnnotationProvider> providers, boolean ellipsis) {
    PsiType result = elementType;
    for (int i = providers.size() - 1; i >= 0; i--) {
      TypeAnnotationProvider provider = providers.get(i);
      result = ellipsis && i == 0 ? new PsiEllipsisType(result, provider) : new PsiArrayType(result, provider);
    }
    providers.clear();
    return result;
  }

  private PsiType inferVarType(PsiElement parent) {
    if (parent instanceof PsiParameter) {
      PsiElement declarationScope = ((PsiParameter)parent).getDeclarationScope();
      if (declarationScope instanceof PsiForeachStatement) {
        PsiExpression iteratedValue = ((PsiForeachStatement)declarationScope).getIteratedValue();
        if (iteratedValue != null) {
          return JavaGenericsUtil.getCollectionItemType(iteratedValue);
        }
        return null;
      }

      if (declarationScope instanceof PsiLambdaExpression) {
        return ((PsiParameter)parent).getType();
      }
    }
    else {
      for (PsiElement e = this; e != null; e = e.getNextSibling()) {
        if (e instanceof PsiExpression) {
          if (!(e instanceof PsiArrayInitializerExpression)) {
            PsiExpression expression = (PsiExpression)e;
            RecursionGuard.StackStamp stamp = RecursionManager.markStack();
            PsiType type = RecursionManager.doPreventingRecursion(expression, true, () -> expression.getType());
            if (stamp.mayCacheNow()) {
              return type == null ? null : JavaVarTypeUtil.getUpwardProjection(type);
            }
            return null;
          }
          return null;
        }
      }
    }
    return null;
  }

  @Override
  public boolean isInferredType() {
    return PsiUtil.isJavaToken(getFirstChild(), JavaTokenType.VAR_KEYWORD) ||
            PsiAugmentProvider.isInferredType(this);
  }

  private static @NotNull ClassReferencePointer getReferenceComputable(@NotNull PsiJavaCodeReferenceElement ref) {
    PsiTypeElement rootType = getRootTypeElement(ref);
    if (rootType != null) {
      PsiElement parent = rootType.getParent();
      if (parent instanceof PsiMethod || parent instanceof PsiVariable) {
        int index = allReferencesInside(rootType).indexOf(ref::equals);
        if (index < 0) throw new AssertionError(rootType.getClass());
        return computeFromTypeOwner(parent, index, new WeakReference<>(ref));
      }
    }
    return ClassReferencePointer.constant(ref);
  }

  @Nullable
  private static PsiTypeElement getRootTypeElement(@NotNull PsiJavaCodeReferenceElement ref) {
    PsiElement root = SyntaxTraverser.psiApi()
      .parents(ref.getParent())
      .takeWhile(it -> it instanceof PsiTypeElement || it instanceof PsiReferenceParameterList || it instanceof PsiJavaCodeReferenceElement)
      .last();
    return ObjectUtils.tryCast(root, PsiTypeElement.class);
  }

  @NotNull
  private static JBIterable<PsiJavaCodeReferenceElement> allReferencesInside(@NotNull PsiTypeElement rootType) {
    return SyntaxTraverser.psiTraverser(rootType).filter(PsiJavaCodeReferenceElement.class);
  }

  private static @NotNull ClassReferencePointer computeFromTypeOwner(PsiElement parent, int index,
                                                                     @NotNull WeakReference<PsiJavaCodeReferenceElement> ref) {
    return new ClassReferencePointer() {
      volatile WeakReference<PsiJavaCodeReferenceElement> myCache = ref;

      @Override
      public @Nullable PsiJavaCodeReferenceElement retrieveReference() {
        PsiJavaCodeReferenceElement result = myCache.get();
        if (result == null) {
          PsiType type = calcTypeByParent();
          if (type instanceof PsiClassReferenceType) {
            result = findReferenceByIndex((PsiClassReferenceType)type);
          }
          myCache = new WeakReference<>(result);
        }
        return result;
      }

      @Nullable
      private PsiJavaCodeReferenceElement findReferenceByIndex(PsiClassReferenceType type) {
        PsiTypeElement root = getRootTypeElement(type.getReference());
        return root == null ? null : allReferencesInside(root).get(index);
      }

      @Nullable
      private PsiType calcTypeByParent() {
        if (!parent.isValid()) return null;

        PsiType type = parent instanceof PsiMethod ? ((PsiMethod)parent).getReturnType() : ((PsiVariable)parent).getType();
        if (type instanceof PsiArrayType) { //also, for c-style array, e.g. String args[]
          return type.getDeepComponentType();
        }
        return type;
      }

      @Override
      public @NotNull PsiJavaCodeReferenceElement retrieveNonNullReference() {
        PsiJavaCodeReferenceElement result = retrieveReference();
        if (result == null) {
          PsiType type = calcTypeByParent();
          if (!(type instanceof PsiClassReferenceType)) {
            PsiUtilCore.ensureValid(parent);
            throw new IllegalStateException("No reference type for " + parent.getClass() + "; type: " + (type != null ? type.getClass() : "null"));
          }
          result = findReferenceByIndex((PsiClassReferenceType)type);
          if (result == null) {
            PsiUtilCore.ensureValid(parent);
            throw new RuntimeExceptionWithAttachments("Can't retrieve reference by index " + index + " for " + parent.getClass() + "; type: " + type.getClass(),
                                                      new Attachment("memberType.txt", type.getCanonicalText()));
          }
        }
        return result;
      }

      @Override
      public String toString() {
        String msg = "Type element reference of " + parent.getClass() + " #" + parent.getClass().getSimpleName() + ", index=" + index;
        return parent.isValid() ? msg + " #" + parent.getLanguage() : msg + ", invalid";
      }
    };
  }

  private static @NotNull TypeAnnotationProvider createProvider(@NotNull List<PsiAnnotation> annotations) {
    return TypeAnnotationProvider.Static.create(ContainerUtil.copyAndClear(annotations, PsiAnnotation.ARRAY_FACTORY, true));
  }

  private @NotNull List<PsiType> collectTypes() {
    List<PsiTypeElement> typeElements = PsiTreeUtil.getChildrenOfTypeAsList(this, PsiTypeElement.class);
    return ContainerUtil.map(typeElements, typeElement -> typeElement.getType());
  }

  @Override
  public PsiJavaCodeReferenceElement getInnermostComponentReferenceElement() {
    TreeElement firstChildNode = getFirstChildNode();
    if (firstChildNode == null) return null;
    if (firstChildNode.getElementType() == JavaElementType.TYPE) {
      return SourceTreeToPsiMap.<PsiTypeElement>treeToPsiNotNull(firstChildNode).getInnermostComponentReferenceElement();
    }
    return getReferenceElement();
  }

  private @Nullable PsiJavaCodeReferenceElement getReferenceElement() {
    ASTNode ref = findChildByType(JavaElementType.JAVA_CODE_REFERENCE);
    if (ref == null) return null;
    return (PsiJavaCodeReferenceElement)SourceTreeToPsiMap.treeElementToPsi(ref);
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, this);
    return true;
  }

  @Override
  public PsiAnnotation @NotNull [] getAnnotations() {
    return getType().getAnnotations();
  }

  @Override
  public PsiAnnotation @NotNull [] getApplicableAnnotations() {
    return getAnnotations();
  }

  @Override
  public PsiAnnotation findAnnotation(@NotNull @NonNls String qualifiedName) {
    return PsiImplUtil.findAnnotation(this, qualifiedName);
  }

  @Override
  public @NotNull PsiAnnotation addAnnotation(@NotNull @NonNls String qualifiedName) {
    PsiAnnotation annotation = JavaPsiFacade.getElementFactory(getProject()).createAnnotationFromText('@' + qualifiedName, this);
    PsiElement firstChild = getFirstChild();
    for (PsiElement child = getLastChild(); child != firstChild; child = child.getPrevSibling()) {
      if (PsiUtil.isJavaToken(child, JavaTokenType.LBRACKET) || PsiUtil.isJavaToken(child, JavaTokenType.ELLIPSIS)) {
        return (PsiAnnotation)addBefore(annotation, child);
      }
    }
    if (firstChild instanceof PsiJavaCodeReferenceElement) {
      PsiIdentifier identifier = PsiTreeUtil.getChildOfType(firstChild, PsiIdentifier.class);
      if (identifier != null && identifier != firstChild.getFirstChild()) {
        // qualified reference
        return (PsiAnnotation)firstChild.addBefore(annotation, identifier);
      }
    }
    PsiElement parent = getParent();
    while (parent instanceof PsiTypeElement && ((PsiTypeElement)parent).getType() instanceof PsiArrayType) {
      parent = parent.getParent();
    }
    if (parent instanceof PsiModifierListOwner) {
      PsiModifierList modifierList = ((PsiModifierListOwner)parent).getModifierList();
      if (modifierList != null) {
        PsiTypeParameterList list = parent instanceof PsiTypeParameterListOwner ? ((PsiTypeParameterListOwner)parent).getTypeParameterList() : null;
        if (list == null || list.textMatches("")) {
          return (PsiAnnotation)modifierList.add(annotation);
        }
      }
    }
    return (PsiAnnotation)addBefore(annotation, firstChild);
  }

  @Override
  public PsiElement replace(@NotNull PsiElement newElement) throws IncorrectOperationException {
    // neighbouring type annotations are logical part of this type element and should be dropped
    //if replacement is `var`, annotations should be left as they are not inferred from the right side of the assignment
    if (!(newElement instanceof PsiTypeElement) || !((PsiTypeElement)newElement).isInferredType()) {
      PsiImplUtil.markTypeAnnotations(this);
    }
    PsiElement result = super.replace(newElement);
    if (result instanceof PsiTypeElement) {
      PsiImplUtil.deleteTypeAnnotations((PsiTypeElement)result);
    }
    return result;
  }

  @Override
  public boolean acceptsAnnotations() {
    if (isInferredType()) return false;
    PsiType type = getType();
    return !PsiType.VOID.equals(type) && !PsiType.NULL.equals(type);
  }

  @Override
  public String toString() {
    return "PsiTypeElement:" + getText();
  }
}
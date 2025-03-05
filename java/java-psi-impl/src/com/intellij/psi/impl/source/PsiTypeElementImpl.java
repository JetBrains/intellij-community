// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source;

import com.intellij.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.RecursionGuard;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.*;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.PsiJavaParserFacadeImpl;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.*;

import java.lang.ref.WeakReference;
import java.util.Arrays;
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
    PsiElement firstChild = getFirstChild();
    if (firstChild == null && parent instanceof PsiUnnamedPattern) {
      type = JavaPsiPatternUtil.getDeconstructedImplicitPatternType((PsiPattern)parent);
    }
    for (PsiElement child = firstChild; child != null; child = child.getNextSibling()) {
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

      if (PsiUtil.isJavaToken(child, JavaTokenType.QUEST)) {
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
      else {
        if (child instanceof ASTNode) {
          ((ASTNode)child).getElementType();
        }
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

    if (type == null) return PsiTypes.nullType();

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
      PsiParameter parameter = (PsiParameter)parent;
      if (parameter instanceof PsiPatternVariable) {
        return JavaPsiPatternUtil.getDeconstructedImplicitPatternVariableType((PsiPatternVariable)parameter);
      }
      PsiElement declarationScope = parameter.getDeclarationScope();
      if (declarationScope instanceof PsiForeachStatement) {
        PsiExpression iteratedValue = ((PsiForeachStatement)declarationScope).getIteratedValue();
        if (iteratedValue != null) {
          PsiType type = JavaGenericsUtil.getCollectionItemType(iteratedValue);
          //Upward projection is applied to the type of the initializer when determining the type of the
          //variable
          return type != null ? JavaVarTypeUtil.getUpwardProjection(type) : null;
        }
        return null;
      }

      if (declarationScope instanceof PsiLambdaExpression) {
        return parameter.getType();
      }
    }
    else {
      for (PsiElement e = this; e != null; e = e.getNextSibling()) {
        if (e instanceof PsiExpression) {
          if (!PsiTreeUtil.processElements(
            e, PsiReferenceExpression.class, ref -> !ref.isReferenceTo(parent))) {
            return null;
          }
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
    PsiElement root = SyntaxTraverser.psiApi()
      .parents(ref.getParent())
      .takeWhile(it -> it instanceof PsiTypeElement || it instanceof PsiReferenceParameterList || it instanceof PsiJavaCodeReferenceElement)
      .last();
    PsiElement parent = root instanceof PsiTypeElement ? root.getParent() : null;
    if (parent instanceof PsiMethod || parent instanceof PsiVariable) {
      PsiModifierListOwner owner = (PsiModifierListOwner)parent;
      int[] pathFromRoot = getPathFromRoot(ref);
      return computeFromTypeOwner(owner, pathFromRoot, new WeakReference<>(ref));
    }
    return ClassReferencePointer.constant(ref);
  }
  
  // n = -1 => go to qualifier
  // n >= 0 => go to type parameter #n
  private static int[] getPathFromRoot(@NotNull PsiJavaCodeReferenceElement ref) {
    IntList result = null;
    while(true) {
      PsiElement parent = ref.getParent();
      if (parent instanceof PsiJavaCodeReferenceElement) {
        PsiJavaCodeReferenceElement parentRef = (PsiJavaCodeReferenceElement)parent;
        if (parentRef.getQualifier() == ref) {
          if (result == null) result = new IntArrayList();
          result.add(0, -1);
          ref = parentRef;
        } else {
          throw new IllegalStateException("Unexpected parent (going not from qualifier): " + parent.getText());
        }
      } else if (parent instanceof PsiTypeElement) {
        PsiElement nextParent = parent.getParent();
        while (nextParent instanceof PsiTypeElement) {
          parent = nextParent;
          nextParent = nextParent.getParent();
        }
        if (nextParent instanceof PsiReferenceParameterList) {
          PsiReferenceParameterList list = (PsiReferenceParameterList)nextParent;
          int index = ArrayUtil.indexOf(list.getTypeParameterElements(), parent);
          PsiElement nextRef = nextParent.getParent();
          if (!(nextRef instanceof PsiJavaCodeReferenceElement)) {
            throw new IllegalStateException("Must be a reference: " + nextRef.getText());
          }
          ref = (PsiJavaCodeReferenceElement)nextRef;
          if (result == null) result = new IntArrayList();
          result.add(0, index);
        } else {
          return result == null ? ArrayUtil.EMPTY_INT_ARRAY : result.toIntArray();
        }
      } else {
        throw new IllegalStateException("Unexpected parent: " + parent.getText());
      }
    }
  }

  @Contract("_,_,true -> !null")
  private static @Nullable PsiJavaCodeReferenceElement findReference(@NotNull PsiType type, int[] root, boolean check) {
    int offset = 0;
    while (true) {
      if (type instanceof PsiWildcardType) {
        PsiType bound = ((PsiWildcardType)type).getBound();
        if (bound == null) {
          if (check) {
            throw new IllegalStateException("Bound expected: " + type.getCanonicalText());
          }
          return null;
        }
        type = bound;
      }
      type = type.getDeepComponentType();
      if (!(type instanceof PsiClassReferenceType)) {
        if (check) {
          throw new IllegalStateException("Reference type expected: " + type.getCanonicalText());
        }
        return null;
      }
      PsiClassReferenceType classType = (PsiClassReferenceType)type;
      PsiJavaCodeReferenceElement ref = classType.getReference();
      if (offset == root.length) return ref;
      int nextIndex = root[offset++];
      while (nextIndex == -1) {
        PsiElement qualifier = ref.getQualifier();
        if (!(qualifier instanceof PsiJavaCodeReferenceElement)) {
          if (check) {
            throw new IllegalStateException("Qualifier expected: " + ref.getCanonicalText());
          }
          return null;
        }
        ref = (PsiJavaCodeReferenceElement)qualifier;
        if (offset == root.length) return ref;
        nextIndex = root[offset++];
      }
      PsiReferenceParameterList list = ref.getParameterList();
      if (list == null) {
        if (check) {
          throw new IllegalStateException("Parameter list expected: " + ref.getCanonicalText());
        }
        return null;
      }
      PsiType[] arguments = list.getTypeArguments();
      if (nextIndex >= arguments.length) {
        if (check) {
          throw new IllegalStateException("Type parameter #" + nextIndex + " expected: " + ref.getCanonicalText());
        }
        return null;
      }
      type = arguments[nextIndex];
    }
  }

  private static @NotNull ClassReferencePointer computeFromTypeOwner(@NotNull PsiModifierListOwner parent, int[] pathFromRoot,
                                                                     @NotNull WeakReference<PsiJavaCodeReferenceElement> ref) {
    return new ClassReferencePointer() {

      @Contract("true -> !null")
      private @Nullable PsiJavaCodeReferenceElement retrieveReference(boolean check) {
        PsiJavaCodeReferenceElement element = ref.get();
        if (element != null && element.isValid()) return element;
        if (check) {
          PsiUtilCore.ensureValid(parent);
        }
        if (!parent.isValid()) return null;
        PsiType type = PsiUtil.getTypeByPsiElement(parent);
        if (type == null) {
          if (check) {
            throw new IllegalStateException("Type of " + parent.getClass() + " is null");
          }
          return null;
        }
        return findReference(type, pathFromRoot, check);
      }

      @Override
      public @Nullable PsiJavaCodeReferenceElement retrieveReference() {
        return retrieveReference(false);
      }

      @Override
      public @NotNull PsiJavaCodeReferenceElement retrieveNonNullReference() {
        return retrieveReference(true);
      }

      @Override
      public String toString() {
        String msg = "Type element reference of " + parent.getClass() + " #" + parent.getClass().getSimpleName() + ", path=" +
                     Arrays.toString(pathFromRoot);
        return parent.isValid() ? msg + " #" + parent.getLanguage() : msg + ", invalid";
      }
    };
  }

  private static @NotNull TypeAnnotationProvider createProvider(@NotNull List<PsiAnnotation> annotations) {
    return TypeAnnotationProvider.Static.create(ContainerUtil.copyAndClear(annotations, PsiAnnotation.ARRAY_FACTORY, true));
  }

  private @Unmodifiable @NotNull List<PsiType> collectTypes() {
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
    return !PsiTypes.voidType().equals(type) && !PsiTypes.nullType().equals(type);
  }

  @Override
  public PsiElement getOriginalElement() {
    PsiElement parent = getParent();
    if (parent instanceof PsiVariable) {
      PsiElement originalVariable = parent.getOriginalElement();
      if (originalVariable != parent && originalVariable instanceof PsiVariable) {
        return ((PsiVariable)originalVariable).getTypeElement();
      }
    }
    if (parent instanceof PsiMethod) {
      PsiElement originalMethod = parent.getOriginalElement();
      if (originalMethod != parent && originalMethod instanceof PsiMethod) {
        return ((PsiMethod)originalMethod).getReturnTypeElement();
      }
    }
    if (parent instanceof PsiTypeElement || parent instanceof PsiJavaCodeReferenceElement ||
        parent instanceof PsiReferenceParameterList) {
      return PsiImplUtil.getCorrespondingOriginalElementOfType(this, PsiTypeElement.class);
    }
    return this;
  }

  @Override
  public String toString() {
    return "PsiTypeElement:" + getText();
  }
}
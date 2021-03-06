// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.javadoc;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.PsiSuperMethodImplUtil;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.scope.DelegatingScopeProcessor;
import com.intellij.psi.scope.ElementClassFilter;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.processor.FilterScopeProcessor;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.CharTable;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class PsiDocMethodOrFieldRef extends CompositePsiElement implements PsiDocTagValue, Constants {
  public PsiDocMethodOrFieldRef() {
    super(DOC_METHOD_OR_FIELD_REF);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitDocTagValue(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public PsiReference getReference() {
    final PsiClass scope = getScope();
    final PsiElement element = getNameElement();
    if (scope == null || element == null) return new MyReference(PsiElement.EMPTY_ARRAY);

    PsiReference psiReference = getReferenceInScope(scope, element);
    if (psiReference != null) return psiReference;

    PsiClass classScope;
    PsiClass containingClass = scope.getContainingClass();
    while (containingClass != null) {
      classScope = containingClass;
      psiReference = getReferenceInScope(classScope, element);
      if (psiReference != null) return psiReference;
      containingClass = classScope.getContainingClass();
    }
    return new MyReference(PsiElement.EMPTY_ARRAY);
  }

  @Nullable
  private PsiReference getReferenceInScope(PsiClass scope, PsiElement element) {
    final String name = element.getText();
    final String[] signature = getSignature();

    if (signature == null) {
      PsiField var = scope.findFieldByName(name, true);
      if (var != null) {
        return new MyReference(new PsiElement[]{var});
      }
    }

    final MethodSignature methodSignature;
    if (signature != null) {
      final List<PsiType> types = new ArrayList<>(signature.length);
      final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(element.getProject());
      for (String s : signature) {
        try {
          types.add(elementFactory.createTypeFromText(s, element));
        }
        catch (IncorrectOperationException e) {
          types.add(PsiType.NULL);
        }
      }
      methodSignature = MethodSignatureUtil.createMethodSignature(name, types.toArray(PsiType.createArray(types.size())),
                                                                  PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY,
                                                                  name.equals(scope.getName()));
    }
    else {
      methodSignature = null;
    }

    PsiMethod[] methods = findMethods(methodSignature, scope, name, getAllMethods(scope, this));

    if (methods.length == 0) return null;

    return new MyReference(methods) {

      @Override
      public void processVariants(@NotNull PsiScopeProcessor processor) {
        super.processVariants(new DelegatingScopeProcessor(processor) {
          @Override
          public boolean execute(@NotNull PsiElement element, @NotNull ResolveState state) {
            if (element instanceof PsiMethod && name.equals(((PsiMethod)element).getName())) {
              return super.execute(element, state);
            }
            return true;
          }
        });
      }
    };
  }

  @Override
  public int getTextOffset() {
    final PsiElement element = getNameElement();
    return element != null ? element.getTextRange().getStartOffset() : getTextRange().getEndOffset();
  }

  @Nullable
  public PsiElement getNameElement() {
    final ASTNode name = findChildByType(DOC_TAG_VALUE_TOKEN);
    return name != null ? SourceTreeToPsiMap.treeToPsiNotNull(name) : null;
  }

  public String @Nullable [] getSignature() {
    PsiElement element = getNameElement();
    if (element == null) return null;

    element = element.getNextSibling();
    while (element != null && !(element instanceof PsiDocTagValue)) {
      element = element.getNextSibling();
    }
    if (element == null) return null;

    List<String> types = new ArrayList<>();
    for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child.getNode().getElementType() == DOC_TYPE_HOLDER) {
        final String[] typeStrings = child.getText().split("[, ]");  //avoid param types list parsing hmm method(paramType1, paramType2, ...) -> typeElement1, identifier2, ...
        for (String type : typeStrings) {
          if (!type.isEmpty()) {
            types.add(type);
          }
        }
      }
    }

    return ArrayUtilRt.toStringArray(types);
  }

  @Nullable
  private PsiClass getScope(){
    if (getFirstChildNode().getElementType() == JavaDocElementType.DOC_REFERENCE_HOLDER) {
      final PsiElement firstChildPsi = SourceTreeToPsiMap.treeElementToPsi(getFirstChildNode().getFirstChildNode());
      if (firstChildPsi instanceof PsiJavaCodeReferenceElement) {
        PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement)firstChildPsi;
        final PsiElement referencedElement = referenceElement.resolve();
        if (referencedElement instanceof PsiClass) return (PsiClass)referencedElement;
        return null;
      }
      else if (firstChildPsi instanceof PsiKeyword) {
        final PsiKeyword keyword = (PsiKeyword)firstChildPsi;

        if (keyword.getTokenType().equals(THIS_KEYWORD)) {
          return JavaResolveUtil.getContextClass(this);
        } else if (keyword.getTokenType().equals(SUPER_KEYWORD)) {
          final PsiClass contextClass = JavaResolveUtil.getContextClass(this);
          if (contextClass != null) return contextClass.getSuperClass();
          return null;
        }
      }
    }
    return JavaResolveUtil.getContextClass(this);
  }

  public static PsiMethod @NotNull [] findMethods(@Nullable MethodSignature methodSignature,
                                                  @NotNull PsiClass scope,
                                                  @Nullable String name,
                                                  PsiMethod @NotNull [] allMethods) {
    final PsiClass superClass = scope.getSuperClass();
    final PsiSubstitutor substitutor = superClass == null ? PsiSubstitutor.EMPTY :
                                       TypeConversionUtil.getSuperClassSubstitutor(superClass, scope, PsiSubstitutor.EMPTY);

    final List<PsiMethod> candidates = new ArrayList<>(Arrays.asList(allMethods));
    final Set<PsiMethod> filteredMethods = new HashSet<>();

    for (PsiMethod method : candidates) {
      if (filteredMethods.contains(method)) {
        continue;
      }

      if (!method.getName().equals(name) ||
          methodSignature != null && !MethodSignatureUtil.areSignaturesErasureEqual(method.getSignature(substitutor), methodSignature)) {
        filteredMethods.add(method);
      }

      PsiSuperMethodImplUtil.getHierarchicalMethodSignature(method)
        .getSuperSignatures()
        .forEach(signature -> filteredMethods.add(signature.getMethod()));
    }

    candidates.removeAll(filteredMethods);
    return candidates.toArray(PsiMethod.EMPTY_ARRAY);
  }

  public static PsiMethod @NotNull [] getAllMethods(PsiElement scope, PsiElement place) {
    final SmartList<PsiMethod> result = new SmartList<>();
    scope.processDeclarations(new FilterScopeProcessor<>(ElementClassFilter.METHOD, result), ResolveState.initial(), null, place);
    return result.toArray(PsiMethod.EMPTY_ARRAY);
  }

  public class MyReference implements PsiJavaReference {
    private final PsiElement[] myReferredElements;

    public MyReference(PsiElement[] referredElements) {
      myReferredElements = referredElements;
    }

    @Override
    public PsiElement resolve() {
      if (myReferredElements.length == 1) return myReferredElements[0];
      return null;
    }

    @Override
    public void processVariants(@NotNull PsiScopeProcessor processor) {
      PsiClass scope = getScope();
      while (scope != null) {
        if (!scope.processDeclarations(new DelegatingScopeProcessor(processor) {
          @Override
          public boolean execute(@NotNull PsiElement element, @NotNull ResolveState state) {
            if (element instanceof PsiMethod || element instanceof PsiField) {
              return super.execute(element, state);
            }
            return true;
          }
        }, ResolveState.initial(), null, PsiDocMethodOrFieldRef.this)) {
          return;
        }
        scope = scope.getContainingClass();
      }
    }

    @Override
    @NotNull
    public JavaResolveResult advancedResolve(boolean incompleteCode) {
      return myReferredElements.length != 1 ? JavaResolveResult.EMPTY
                                            : new CandidateInfo(myReferredElements[0], PsiSubstitutor.EMPTY);
    }

    @Override
    public JavaResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
      return Arrays.stream(myReferredElements)
        .map(myReferredElement -> new CandidateInfo(myReferredElement, PsiSubstitutor.EMPTY))
        .toArray(JavaResolveResult[]::new);
    }

    @Override
    public PsiElement @NotNull [] getVariants(){
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isSoft(){
      return false;
    }

    @Override
    @NotNull
    public String getCanonicalText() {
      final PsiElement nameElement = getNameElement();
      assert nameElement != null;
      return nameElement.getText();
    }

    @Override
    public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
      final PsiElement nameElement = getNameElement();
      assert nameElement != null;
      final ASTNode treeElement = SourceTreeToPsiMap.psiToTreeNotNull(nameElement);
      final CharTable charTableByTree = SharedImplUtil.findCharTableByTree(treeElement);
      final LeafElement newToken = Factory.createSingleLeafElement(DOC_TAG_VALUE_TOKEN, newElementName, charTableByTree, getManager());
      ((CompositeElement)treeElement.getTreeParent()).replaceChildInternal(SourceTreeToPsiMap.psiToTreeNotNull(nameElement), newToken);
      return SourceTreeToPsiMap.treeToPsiNotNull(newToken);
    }

    @Override
    public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
      if (isReferenceTo(element)) return PsiDocMethodOrFieldRef.this;
      final PsiElement nameElement = getNameElement();
      assert nameElement != null;
      final String name = nameElement.getText();
      final String newName;

      final PsiMethod method;
      final PsiField field;
      final boolean hasSignature;
      final PsiClass containingClass;
      if (element instanceof PsiMethod) {
        method = (PsiMethod)element;
        hasSignature = getSignature() != null;
        containingClass = method.getContainingClass();
        newName = method.getName();
      } else if (element instanceof PsiField) {
        field = (PsiField) element;
        hasSignature = false;
        containingClass = field.getContainingClass();
        method = null;
        newName = field.getName();
      } else {
        throw new IncorrectOperationException();
      }

      final PsiElement child = getFirstChild();
      if (containingClass != null && child != null && child.getNode().getElementType() == JavaDocElementType.DOC_REFERENCE_HOLDER) {
        PsiElement ref = child.getFirstChild();
        if (ref instanceof PsiJavaCodeReferenceElement) {
          ((PsiJavaCodeReferenceElement)ref).bindToElement(containingClass);
        }
      }
      else {
        if (containingClass != null && !PsiTreeUtil.isAncestor(containingClass, PsiDocMethodOrFieldRef.this, true)) {
          PsiDocComment fromText = JavaPsiFacade.getElementFactory(containingClass.getProject())
            .createDocCommentFromText("/**{@link " + containingClass.getQualifiedName() + "#" + newName + "}*/");
          PsiDocMethodOrFieldRef methodOrFieldRefFromText = PsiTreeUtil.findChildOfType(fromText, PsiDocMethodOrFieldRef.class);
          addAfter(Objects.requireNonNull(methodOrFieldRefFromText).getFirstChild(), null);
        }
      }

      if (hasSignature || !name.equals(newName)) {
        String text = getText();

        @NonNls StringBuffer newText = new StringBuffer();
        newText.append("/** @see ");
        if (name.equals(newName)) { // hasSignature is true here, so we can search for '('
          newText.append(text, 0, text.indexOf('('));
        }
        else {
          final int sharpIndex = text.indexOf('#');
          if (sharpIndex >= 0) {
            newText.append(text, 0, sharpIndex + 1);
          }
          newText.append(newName);
        }
        if (hasSignature) {
          newText.append('(');
          PsiParameter[] parameters = method.getParameterList().getParameters();
          newText.append(StringUtil.join(parameters, parameter -> TypeConversionUtil.erasure(parameter.getType()).getCanonicalText(), ","));
          newText.append(')');
        }
        newText.append("*/");

        return bindToText(containingClass, newText);
      }

      return PsiDocMethodOrFieldRef.this;
    }

    public PsiElement bindToText(PsiClass containingClass, StringBuffer newText) {
      PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(containingClass.getProject());
      PsiComment comment = elementFactory.createCommentFromText(newText.toString(), null);
      PsiElement tag = PsiTreeUtil.getChildOfType(comment, PsiDocTag.class);
      PsiElement ref = PsiTreeUtil.getChildOfType(tag, PsiDocMethodOrFieldRef.class);
      assert ref != null : newText;
      return replace(ref);
    }

    @Override
    public boolean isReferenceTo(@NotNull PsiElement element) {
      PsiManagerEx manager = getManager();
      for (PsiElement myReferredElement : myReferredElements) {
        if (manager.areElementsEquivalent(element, myReferredElement)) return true;
      }
      return false;
    }

    @NotNull
    @Override
    public TextRange getRangeInElement() {
      final ASTNode sharp = findChildByType(DOC_TAG_VALUE_SHARP_TOKEN);
      if (sharp == null) return new TextRange(0, getTextLength());
      final PsiElement nextSibling = SourceTreeToPsiMap.treeToPsiNotNull(sharp).getNextSibling();
      if (nextSibling != null) {
        final int startOffset = nextSibling.getTextRange().getStartOffset() - getTextRange().getStartOffset();
        int endOffset = nextSibling.getTextRange().getEndOffset() - getTextRange().getStartOffset();
        return new TextRange(startOffset, endOffset);
      }
      return new TextRange(getTextLength(), getTextLength());
    }

    @NotNull
    @Override
    public PsiElement getElement() {
      return PsiDocMethodOrFieldRef.this;
    }
  }
}

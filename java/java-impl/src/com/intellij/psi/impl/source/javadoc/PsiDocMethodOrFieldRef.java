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
package com.intellij.psi.impl.source.javadoc;

import com.google.common.collect.Lists;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.scope.ElementClassFilter;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.processor.FilterScopeProcessor;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.CharTable;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author mike
 */
public class PsiDocMethodOrFieldRef extends CompositePsiElement implements PsiDocTagValue, Constants {
  public PsiDocMethodOrFieldRef() {
    super(DOC_METHOD_OR_FIELD_REF);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitDocTagValue(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public PsiReference getReference() {
    final PsiElement scope = getScope();
    final PsiElement element = getNameElement();
    if (scope == null || element == null) return new MyReference(null);

    PsiReference psiReference = getReferenceInScope(scope, element);
    if (psiReference != null) return psiReference;

    if (scope instanceof PsiClass) {
      PsiClass classScope = ((PsiClass)scope);
      PsiClass containingClass = classScope.getContainingClass();
      while (containingClass != null) {
        classScope = containingClass;
        psiReference = getReferenceInScope(classScope, element);
        if (psiReference != null) return psiReference;
        containingClass = classScope.getContainingClass();
      }
    }
    return new MyReference(null);
  }

  @Nullable
  private PsiReference getReferenceInScope(PsiElement scope, PsiElement element) {
    final String name = element.getText();
    final String[] signature = getSignature();

    if (signature == null) {
      final PsiVariable[] vars = getAllVariables(scope, this);
      for (PsiVariable var : vars) {
        if (!var.getName().equals(name)) continue;
        return new MyReference(var);
      }
    }

    final MethodSignature methodSignature;
    if (signature != null) {
      final List<PsiType> types = Lists.newArrayListWithCapacity(signature.length);
      final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(element.getProject()).getElementFactory();
      for (String s : signature) {
        try {
          types.add(elementFactory.createTypeFromText(s, element));
        }
        catch (IncorrectOperationException e) {
          types.add(PsiType.NULL);
        }
      }
      methodSignature = MethodSignatureUtil.createMethodSignature(name, types.toArray(new PsiType[types.size()]),
                                                                  PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY);
    }
    else {
      methodSignature = null;
    }

    final PsiMethod[] methods = getAllMethods(scope, this);
    for (PsiMethod method : methods) {
      if (!method.getName().equals(name) ||
          (methodSignature != null && !MethodSignatureUtil.areSignaturesErasureEqual(methodSignature, method.getSignature(PsiSubstitutor.EMPTY)))) continue;
      return new MyReference(method) {
        @NotNull
        public PsiElement[] getVariants() {
          final List<PsiMethod> lst = new ArrayList<PsiMethod>();
          for (PsiMethod method : methods) {
            if (name.equals(method.getName())) {
              lst.add(method);
            }
          }
          return lst.toArray(new PsiMethod[lst.size()]);
        }
      };
    }

    return null;
  }

  public static PsiVariable[] getAllVariables(PsiElement scope, PsiElement place) {
    final SmartList<PsiVariable> result = new SmartList<PsiVariable>();
    scope.processDeclarations(new FilterScopeProcessor<PsiVariable>(ElementClassFilter.VARIABLE, result), ResolveState.initial(), null, place);
    return result.toArray(new PsiVariable[result.size()]);
  }

  public static PsiMethod[] getAllMethods(PsiElement scope, PsiElement place) {
    final SmartList<PsiMethod> result = new SmartList<PsiMethod>();
    scope.processDeclarations(new FilterScopeProcessor<PsiMethod>(ElementClassFilter.METHOD, result), ResolveState.initial(), null, place);
    return result.toArray(new PsiMethod[result.size()]);
  }

  public int getTextOffset() {
    final PsiElement element = getNameElement();
    return element != null ? element.getTextRange().getStartOffset() : getTextRange().getEndOffset();
  }

  @Nullable
  public PsiElement getNameElement() {
    final ASTNode sharp = findChildByType(DOC_TAG_VALUE_SHARP_TOKEN);
    return sharp != null ? SourceTreeToPsiMap.treeToPsiNotNull(sharp).getNextSibling() : null;
  }

  @Nullable
  public String[] getSignature() {
    PsiElement element = getNameElement();
    if (element == null) return null;

    element = element.getNextSibling();
    while (element != null && !(element instanceof PsiDocTagValue)) {
      element = element.getNextSibling();
    }
    if (element == null) return null;

    List<String> types = new ArrayList<String>();
    for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child.getNode().getElementType() == DOC_TYPE_HOLDER) {
        final String[] typeStrings = child.getText().split("[, ]");  //avoid param types list parsing hmm method(paramType1, paramType2, ...) -> typeElement1, identifier2, ...
        if (typeStrings != null) {
          for (String type : typeStrings) {
            if (type.length() > 0) {
              types.add(type);
            }
          }
        }
      }
    }

    return ArrayUtil.toStringArray(types);
  }

  @Nullable
  private PsiElement getScope(){
    if (getFirstChildNode().getElementType() == ElementType.DOC_REFERENCE_HOLDER) {
      final PsiElement firstChildPsi = SourceTreeToPsiMap.treeElementToPsi(getFirstChildNode().getFirstChildNode());
      if (firstChildPsi instanceof PsiJavaCodeReferenceElement) {
        PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement)firstChildPsi;
        final PsiElement referencedElement = referenceElement.resolve();
        if (referencedElement instanceof PsiClass) return referencedElement;
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

  public class MyReference implements PsiJavaReference {
    private final PsiElement myReferredElement;

    public MyReference(PsiElement referredElement) {
      myReferredElement = referredElement;
    }

    public PsiElement resolve() {
      return myReferredElement;
    }

    public void processVariants(PsiScopeProcessor processor) {
      for (final PsiElement element : getVariants()) {
        if (!processor.execute(element, ResolveState.initial())) {
          return;
        }
      }
    }

    @NotNull
    public JavaResolveResult advancedResolve(boolean incompleteCode) {
      return myReferredElement == null ? JavaResolveResult.EMPTY
                                  : new CandidateInfo(myReferredElement, PsiSubstitutor.EMPTY);
    }

    @NotNull
    public JavaResolveResult[] multiResolve(boolean incompleteCode) {
      return myReferredElement == null ? JavaResolveResult.EMPTY_ARRAY
                                  : new JavaResolveResult[]{new CandidateInfo(myReferredElement, PsiSubstitutor.EMPTY)};
    }

    @NotNull
    public PsiElement[] getVariants(){
      final List<PsiModifierListOwner> vars = new ArrayList<PsiModifierListOwner>();
      final PsiElement scope = getScope();
      if (scope != null) {
        ContainerUtil.addAll(vars, getAllMethods(scope, PsiDocMethodOrFieldRef.this));
        ContainerUtil.addAll(vars, getAllVariables(scope, PsiDocMethodOrFieldRef.this));
      }
      return vars.toArray(new PsiModifierListOwner[vars.size()]);
    }

    public boolean isSoft(){
      return false;
    }

    @NotNull
    public String getCanonicalText() {
      final PsiElement nameElement = getNameElement();
      assert nameElement != null;
      return nameElement.getText();
    }

    public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
      final PsiElement nameElement = getNameElement();
      assert nameElement != null;
      final ASTNode treeElement = SourceTreeToPsiMap.psiToTreeNotNull(nameElement);
      final CharTable charTableByTree = SharedImplUtil.findCharTableByTree(treeElement);
      final LeafElement newToken = Factory.createSingleLeafElement(DOC_TAG_VALUE_TOKEN, newElementName, charTableByTree, getManager());
      ((CompositeElement)treeElement.getTreeParent()).replaceChildInternal(SourceTreeToPsiMap.psiToTreeNotNull(nameElement), newToken);
      return SourceTreeToPsiMap.treeToPsiNotNull(newToken);
    }

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
      if (containingClass != null && child != null && child.getNode().getElementType() == ElementType.DOC_REFERENCE_HOLDER) {
        final PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement) child.getFirstChild();
        assert referenceElement != null;
        referenceElement.bindToElement(containingClass);
      }
      else {
        if (containingClass != null && !PsiTreeUtil.isAncestor(containingClass, PsiDocMethodOrFieldRef.this, true)) {
          final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(containingClass.getProject()).getElementFactory();
          final PsiReferenceExpression ref = elementFactory.createReferenceExpression(containingClass);
          addAfter(ref, null);
        }
      }

      if (hasSignature || !name.equals(newName)) {
        String text = getText();

        @NonNls StringBuffer newText = new StringBuffer();
        newText.append("/** @see ");
        if (name.equals(newName)) { // hasSignature is true here, so we can search for '('
          newText.append(text.substring(0, text.indexOf('(')));
        }
        else {
          final int sharpIndex = text.indexOf('#');
          if (sharpIndex >= 0) {
            newText.append(text.substring(0, sharpIndex + 1));
          }
          newText.append(newName);
        }
        if (hasSignature) {
          newText.append('(');
          PsiParameter[] parameters = method.getParameterList().getParameters();
          for (int i = 0; i < parameters.length; i++) {
            PsiParameter parameter = parameters[i];
            if (i > 0) newText.append(",");
            newText.append(parameter.getType().getCanonicalText());
          }
          newText.append(')');
        }
        newText.append("*/");

        return bindToText(containingClass, newText);
      }

      return PsiDocMethodOrFieldRef.this;
    }

    public PsiElement bindToText(PsiClass containingClass, StringBuffer newText) {
      PsiElementFactory elementFactory = JavaPsiFacade.getInstance(containingClass.getProject()).getElementFactory();
      PsiComment comment = elementFactory.createCommentFromText(newText.toString(), null);
      PsiElement tag = PsiTreeUtil.getChildOfType(comment, PsiDocTag.class);
      PsiElement ref = PsiTreeUtil.getChildOfType(tag, PsiDocMethodOrFieldRef.class);
      assert ref != null : newText;
      return replace(ref);
    }

    public boolean isReferenceTo(PsiElement element) {
      return getManager().areElementsEquivalent(resolve(), element);
    }

    public TextRange getRangeInElement() {
      final ASTNode sharp = findChildByType(DOC_TAG_VALUE_SHARP_TOKEN);
      if (sharp == null) return new TextRange(0, getTextLength());
      final PsiElement nextSibling = SourceTreeToPsiMap.treeToPsiNotNull(sharp).getNextSibling();
      if (nextSibling != null) {
        final int startOffset = nextSibling.getTextRange().getStartOffset() - getTextRange().getStartOffset();
        int endOffset = nextSibling.getTextRange().getEndOffset() - getTextRange().getStartOffset();
        final PsiElement nextParSibling = nextSibling.getNextSibling();
        if (nextParSibling != null && "(".equals(nextParSibling.getText())) {
          endOffset++;
          PsiElement nextElement = nextParSibling.getNextSibling();
          if (nextElement != null && SourceTreeToPsiMap.psiToTreeNotNull(nextElement).getElementType() == DOC_TAG_VALUE_TOKEN) {
            endOffset += nextElement.getTextLength();
            nextElement = nextElement.getNextSibling();
          }
          if (nextElement != null && ")".equals(nextElement.getText())) {
            endOffset++;
          }
        }
        return new TextRange(startOffset, endOffset);
      }
      return new TextRange(getTextLength(), getTextLength());
    }

    public PsiElement getElement() {
      return PsiDocMethodOrFieldRef.this;
    }
  }
}

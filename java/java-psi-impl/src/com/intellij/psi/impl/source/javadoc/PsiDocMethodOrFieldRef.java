// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.javadoc;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.JavaResolveResult;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaReference;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.ResolveState;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.PsiSuperMethodImplUtil;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.Factory;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.impl.source.tree.SharedImplUtil;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.scope.DelegatingScopeProcessor;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class PsiDocMethodOrFieldRef extends CompositePsiElement implements PsiDocTagValue, Constants {
  private static final List<String> SIGNATURE_TO_REPLACE = Arrays.asList("\\[", "\\]");
  private static final List<String> SIGNATURE_REPLACEMENT = Arrays.asList("[", "]");
  
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

  private @Nullable PsiReference getReferenceInScope(PsiClass scope, PsiElement element) {
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
          types.add(PsiTypes.nullType());
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

  public @Nullable PsiElement getNameElement() {
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
        // JEP-467: Markdown comments have escaped brackets for array types 
        types.add(Strings.replace(child.getText(), SIGNATURE_TO_REPLACE, SIGNATURE_REPLACEMENT));
      }
    }

    return ArrayUtilRt.toStringArray(types);
  }

  private @Nullable PsiClass getScope() {
    return getScope(this);
  }

  /**
   * Returns the PsiClass targeted by the given reference element (e.g. {@code MyClass#…} or {@code MyClass##…}).
   */
  public static @Nullable PsiClass getScope(CompositePsiElement ref) {
    final TreeElement firstChildNode = ref.getFirstChildNode();
    if (firstChildNode != null && firstChildNode.getElementType() == DOC_REFERENCE_HOLDER) {
      final PsiElement firstChildPsi = SourceTreeToPsiMap.treeElementToPsi(firstChildNode.getFirstChildNode());
      if (firstChildPsi instanceof PsiJavaCodeReferenceElement) {
        PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement)firstChildPsi;
        final PsiElement referencedElement = referenceElement.resolve();
        if (referencedElement instanceof PsiClass) return (PsiClass)referencedElement;
        return null;
      }
      else if (firstChildPsi instanceof PsiKeyword) {
        final PsiKeyword keyword = (PsiKeyword)firstChildPsi;

        if (keyword.getTokenType().equals(THIS_KEYWORD)) {
          return JavaResolveUtil.getContextClass(ref);
        }
        else if (keyword.getTokenType().equals(SUPER_KEYWORD)) {
          final PsiClass contextClass = JavaResolveUtil.getContextClass(ref);
          if (contextClass != null) return contextClass.getSuperClass();
          return null;
        }
      }
    }
    return JavaResolveUtil.getContextClass(ref);
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

  public static PsiMethod @NotNull [] getAllMethods(PsiClass scope, PsiElement place) {
    final SmartList<PsiMethod> result = new SmartList<>();
    scope.processDeclarations(new FilterScopeProcessor<>(new ElementFilter() {
      @Override
      public boolean isAcceptable(Object element, @Nullable PsiElement context) {
        if (element instanceof PsiMethod) {
          if (!scope.isInterface()) {
            return true;
          }
          PsiClass containingClass = ((PsiMethod)element).getContainingClass();
          return containingClass != null && !CommonClassNames.JAVA_LANG_OBJECT.equals(containingClass.getQualifiedName());
        }
        return false;
      }

      @Override
      public boolean isClassAcceptable(Class hintClass) {
        return true;
      }
    }, result), ResolveState.initial(), null, place);
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
    public @NotNull JavaResolveResult advancedResolve(boolean incompleteCode) {
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
    public @NotNull String getCanonicalText() {
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
      else if (containingClass != null && PsiTreeUtil.getParentOfType(PsiDocMethodOrFieldRef.this, PsiClass.class) != containingClass) {
        String qName = containingClass.getQualifiedName();
        if (qName == null) qName = containingClass.getName(); // local class has no qualified name, but has a short name
        if (qName == null) return PsiDocMethodOrFieldRef.this; // ref can't be fixed
        PsiDocComment fromText = JavaPsiFacade.getElementFactory(containingClass.getProject())
          .createDocCommentFromText("/**{@link " + qName + "#" + newName + "}*/");
        PsiDocMethodOrFieldRef methodOrFieldRefFromText = PsiTreeUtil.findChildOfType(fromText, PsiDocMethodOrFieldRef.class);
        addAfter(Objects.requireNonNull(methodOrFieldRefFromText).getFirstChild(), null);
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

        return bindToText(newText);
      }

      return PsiDocMethodOrFieldRef.this;
    }

    public PsiElement bindToText(StringBuffer newText) {
      PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(getProject());
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

    @Override
    public @NotNull TextRange getRangeInElement() {
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

    @Override
    public @NotNull PsiElement getElement() {
      return PsiDocMethodOrFieldRef.this;
    }
  }
}

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
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaReference;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.ResolveState;
import com.intellij.psi.filters.ElementFilter;
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
import com.intellij.psi.javadoc.PsiDocReferenceHolder;
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
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/// PsiElement that is a _guarantee_ to reference either a **method** or a **field**.
///
/// @see PsiDocReferenceHolder PsiDocReferenceHolder for other ways to reference methods and fields
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
  public @Nullable PsiReference getReference() {
    return getReference(this);
  }

  @Override
  public int getTextOffset() {
    final PsiElement element = getNameElement();
    return element != null ? element.getTextRange().getStartOffset() : getTextRange().getEndOffset();
  }

  public @Nullable PsiElement getNameElement() {
    return getNameElement(this);
  }

  public String @Nullable [] getSignature() {
    return getSignature(this);
  }

  /**
   * Returns the PsiClass targeted by the given reference element (e.g. {@code MyClass#…} or {@code MyClass##…}).
   */
  public static @Nullable PsiClass getScope(PsiElement ref) {
    if (ref instanceof TreeElement) {
      final TreeElement firstChildNode = ((TreeElement)ref).getFirstChildNode();
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

      if (!method.getName().equals(name)) {
        filteredMethods.add(method);
      }
      else if (methodSignature != null) {
        // Since jdk 15, doc methods may be parameterized. If they are, the resolution must take into account the generic types
        PsiType[] types = methodSignature.getParameterTypes();
        boolean followStrictSignature = ContainerUtil.exists(types, type -> {
          if (type instanceof PsiClassType) {
            return ((PsiClassType)type).getParameterCount() > 0;
          }
          return false;
        });

        boolean equals = followStrictSignature
                         ? MethodSignatureUtil.areSignaturesEqual(method.getSignature(substitutor), methodSignature)
                         : MethodSignatureUtil.areSignaturesErasureEqual(method.getSignature(substitutor), methodSignature);

        if (!equals) {
          filteredMethods.add(method);
        }
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

  /// Get the doc tag value name from the psi tree
  private static @Nullable PsiElement getNameElement(PsiElement element) {
    ASTNode name = element.getNode().findChildByType(DOC_TAG_VALUE_TOKEN);
    if (name != null) {
      return SourceTreeToPsiMap.treeToPsiNotNull(name);
    }
    name = element.getNode().findChildByType(JAVA_CODE_REFERENCE);
    if (name != null) {
      return SourceTreeToPsiMap.treeToPsiNotNull(name);
    }
    return null;
  }

  private static String @Nullable [] getSignature(PsiElement element) {
    PsiElement nameElement = getNameElement(element);
    if (nameElement == null) return null;

    nameElement = nameElement.getNextSibling();
    while (nameElement != null && !(nameElement instanceof PsiDocTagValue)) {
      nameElement = nameElement.getNextSibling();
    }
    if (nameElement == null) return null;

    List<String> types = new ArrayList<>();
    for (PsiElement child = nameElement.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child.getNode().getElementType() == DOC_TYPE_HOLDER) {
        // JEP-467: Markdown comments have escaped brackets for array types 
        types.add(Strings.replace(child.getText(), SIGNATURE_TO_REPLACE, SIGNATURE_REPLACEMENT));
      }
    }

    return ArrayUtilRt.toStringArray(types);
  }

  /// Tries to get the reference in an ever larger scope
  ///
  /// @return The reference to a method or a ref
  public static @NotNull PsiDocMethodOrFieldRef.MethodOrFieldReference getReference(PsiElement element) {
    final PsiClass scope = getScope(element);
    final PsiElement nameElement = getNameElement(element);
    if (scope == null || nameElement == null) return new MethodOrFieldReference(element, PsiElement.EMPTY_ARRAY);

    MethodOrFieldReference psiReference = getReferenceInScope(element, scope, nameElement);
    if (psiReference != null) return psiReference;

    PsiClass classScope = null;
    PsiClass containingClass = scope.getContainingClass();
    while (containingClass != null && classScope != containingClass) {
      classScope = containingClass;
      psiReference = getReferenceInScope(element, classScope, nameElement);
      if (psiReference != null) return psiReference;
      containingClass = classScope.getContainingClass();
    }
    return new MethodOrFieldReference(element, PsiElement.EMPTY_ARRAY);
  }

  /// @return The reference if found in the given scope
  private static @Nullable PsiDocMethodOrFieldRef.MethodOrFieldReference getReferenceInScope(PsiElement referringElement,
                                                                                             PsiClass scope,
                                                                                             PsiElement element) {
    final String name = element.getText();
    final String[] signature = getSignature(referringElement);

    if (signature == null) {
      PsiField var = scope.findFieldByName(name, true);
      if (var != null) {
        return new MethodOrFieldReference(referringElement, new PsiElement[]{var});
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

    PsiMethod[] methods = findMethods(methodSignature, scope, name, getAllMethods(scope, referringElement));

    if (methods.length == 0) return null;

    return new MethodOrFieldReference(referringElement, methods) {

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

  /// Reference to a Java element made from the Javadoc
  public static class MethodOrFieldReference implements PsiJavaReference {
    private final PsiElement myReferringElement;
    private final PsiElement[] myReferredElements;

    public MethodOrFieldReference(PsiElement element, PsiElement[] referredElements) {
      myReferringElement = element;
      myReferredElements = referredElements;
    }

    @Override
    public PsiElement resolve() {
      if (myReferredElements.length == 1) return myReferredElements[0];
      return null;
    }

    @Override
    public void processVariants(@NotNull PsiScopeProcessor processor) {
      PsiClass scope = getScope(myReferringElement);
      while (scope != null) {
        if (!scope.processDeclarations(new DelegatingScopeProcessor(processor) {
          @Override
          public boolean execute(@NotNull PsiElement element, @NotNull ResolveState state) {
            if (element instanceof PsiMethod || element instanceof PsiField) {
              return super.execute(element, state);
            }
            return true;
          }
        }, ResolveState.initial(), null, myReferringElement)) {
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
      final PsiElement nameElement = getNameElement(myReferringElement);
      assert nameElement != null;
      return nameElement.getText();
    }

    @Override
    public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
      final PsiElement nameElement = getNameElement(myReferringElement);
      assert nameElement != null;
      final ASTNode treeElement = SourceTreeToPsiMap.psiToTreeNotNull(nameElement);
      final CharTable charTableByTree = SharedImplUtil.findCharTableByTree(treeElement);
      final LeafElement newToken =
        Factory.createSingleLeafElement(DOC_TAG_VALUE_TOKEN, newElementName, charTableByTree, myReferringElement.getManager());
      ((CompositeElement)treeElement.getTreeParent()).replaceChildInternal(SourceTreeToPsiMap.psiToTreeNotNull(nameElement), newToken);
      return SourceTreeToPsiMap.treeToPsiNotNull(newToken);
    }

    @Override
    public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
      if (isReferenceTo(element)) return myReferringElement;
      final PsiElement nameElement = getNameElement(myReferringElement);
      assert nameElement != null;
      final String name = nameElement.getText();
      final String newName;

      final PsiMethod method;
      final PsiField field;
      final boolean hasSignature;
      final PsiClass containingClass;
      if (element instanceof PsiMethod) {
        method = (PsiMethod)element;
        hasSignature = getSignature(myReferringElement) != null;
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

      final PsiElement child = myReferringElement.getFirstChild();
      if (containingClass != null && child != null && child.getNode().getElementType() == JavaDocElementType.DOC_REFERENCE_HOLDER) {
        PsiElement ref = child.getFirstChild();
        if (ref instanceof PsiJavaCodeReferenceElement) {
          ((PsiJavaCodeReferenceElement)ref).bindToElement(containingClass);
        }
      }
      else if (containingClass != null && PsiTreeUtil.getParentOfType(myReferringElement, PsiClass.class) != containingClass) {
        String qName = containingClass.getQualifiedName();
        if (qName == null) qName = containingClass.getName(); // local class has no qualified name, but has a short name
        if (qName == null) return myReferringElement; // ref can't be fixed
        PsiDocComment fromText = JavaPsiFacade.getElementFactory(containingClass.getProject())
          .createDocCommentFromText("/**{@link " + qName + "#" + newName + "}*/");
        PsiDocMethodOrFieldRef methodOrFieldRefFromText = PsiTreeUtil.findChildOfType(fromText, PsiDocMethodOrFieldRef.class);
        myReferringElement.addAfter(Objects.requireNonNull(methodOrFieldRefFromText).getFirstChild(), null);
      }

      if (hasSignature || !name.equals(newName)) {
        String text = myReferringElement.getText();

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

      return myReferringElement;
    }

    public PsiElement bindToText(StringBuffer newText) {
      PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(myReferringElement.getProject());
      PsiComment comment = elementFactory.createCommentFromText(newText.toString(), null);
      PsiElement tag = PsiTreeUtil.getChildOfType(comment, PsiDocTag.class);
      PsiElement ref = PsiTreeUtil.getChildOfType(tag, PsiDocMethodOrFieldRef.class);
      assert ref != null : newText;
      return myReferringElement.replace(ref);
    }

    @Override
    public boolean isReferenceTo(@NotNull PsiElement element) {
      PsiManager manager = myReferringElement.getManager();
      for (PsiElement myReferredElement : myReferredElements) {
        if (manager.areElementsEquivalent(element, myReferredElement)) return true;
      }
      return false;
    }

    @Override
    public @NotNull TextRange getRangeInElement() {
      final ASTNode sharp = myReferringElement.getNode().findChildByType(DOC_TAG_VALUE_SHARP_TOKEN);
      final PsiElement nextSibling = sharp == null ? myReferringElement.getFirstChild() : SourceTreeToPsiMap.treeToPsiNotNull(sharp).getNextSibling();
      if (nextSibling != null) {
        final int startOffset = nextSibling.getTextRange().getStartOffset() - myReferringElement.getTextRange().getStartOffset();
        int endOffset = nextSibling.getTextRange().getEndOffset() - myReferringElement.getTextRange().getStartOffset();
        return new TextRange(startOffset, endOffset);
      }
      return new TextRange(myReferringElement.getTextLength(), myReferringElement.getTextLength());
    }

    @Override
    public @NotNull PsiElement getElement() {
      return myReferringElement;
    }
  }
}

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
package com.intellij.psi.impl.source;

import com.intellij.codeInsight.daemon.impl.analysis.AnnotationsHighlightUtil;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PatchedSoftReference;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PsiTypeElementImpl extends CompositePsiElement implements PsiTypeElement {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.PsiTypeElementImpl");

  private volatile PsiType myCachedType = null;
  private volatile PatchedSoftReference<PsiType> myCachedDetachedType = null;

  @SuppressWarnings({"UnusedDeclaration"})
  public PsiTypeElementImpl() {
    this(JavaElementType.TYPE);
  }

  protected PsiTypeElementImpl(final IElementType type) {
    super(type);
  }

  public void clearCaches() {
    super.clearCaches();
    myCachedType = null;
    myCachedDetachedType = null;
  }

  public void accept(@NotNull PsiElementVisitor visitor){
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitTypeElement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString(){
    return "PsiTypeElement:" + getText();
  }

  @NotNull
  public PsiType getType() {
    PsiType cachedType = myCachedType;
    if (cachedType != null) {
      return cachedType;
    }

    final List<PsiAnnotation> typeAnnotations = new ArrayList<PsiAnnotation>();
    TreeElement element = getFirstChildNode();
    while (element != null) {
      IElementType elementType = element.getElementType();
      if (element.getTreeNext() == null && ElementType.PRIMITIVE_TYPE_BIT_SET.contains(elementType)) {
        addTypeUseAnnotationsFromModifierList(getParent(), typeAnnotations);
        final PsiAnnotation[] array = toAnnotationsArray(typeAnnotations);
        cachedType = JavaPsiFacade.getInstance(getProject()).getElementFactory().createPrimitiveType(element.getText(), array);
        assert cachedType != null;
      }
      else if (elementType == JavaElementType.TYPE) {
        final IElementType tailType = getLastChildNode().getElementType();
        if (tailType == JavaTokenType.ELLIPSIS) {
          final PsiType componentType = ((PsiTypeElement)SourceTreeToPsiMap.treeToPsiNotNull(element)).getType();
          cachedType = new PsiEllipsisType(componentType);
        }
        else if (tailType == JavaTokenType.RBRACKET) {
          final PsiType componentType = ((PsiTypeElement)SourceTreeToPsiMap.treeToPsiNotNull(element)).getType();
          cachedType = componentType.createArrayType();
        }
        else {
          cachedType = new PsiDisjunctionType(this);
        }
      }
      else if (elementType == JavaElementType.JAVA_CODE_REFERENCE) {
        addTypeUseAnnotationsFromModifierList(getParent(), typeAnnotations);
        final PsiAnnotation[] array = toAnnotationsArray(typeAnnotations);
        final PsiJavaCodeReferenceElement reference = SourceTreeToPsiMap.treeToPsiNotNull(element);
        cachedType = new PsiClassReferenceType(reference, null, array);
      }
      else if (elementType == JavaTokenType.QUEST) {
        cachedType = createWildcardType();
      }
      else if (ElementType.JAVA_COMMENT_OR_WHITESPACE_BIT_SET.contains(elementType)) {
        element = element.getTreeNext();
        continue;
      }
      else if (elementType == JavaElementType.ANNOTATION) {
        final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(getProject()).getElementFactory();
        final PsiAnnotation annotation = elementFactory.createAnnotationFromText(element.getText(), this);
        typeAnnotations.add(annotation);
        element = element.getTreeNext();
        continue;
      }
      else if (elementType == JavaElementType.DIAMOND_TYPE) {
        cachedType = new PsiDiamondType(getManager(), this);
        break;
      }
      else {
        LOG.error("Unknown element type: " + elementType);
      }
      if (element.getTextLength() != 0) break;
      element = element.getTreeNext();
    }

    if (cachedType == null) cachedType = PsiType.NULL;
    myCachedType = cachedType;
    return cachedType;
  }

  private static PsiAnnotation[] toAnnotationsArray(List<PsiAnnotation> typeAnnotations) {
    final int size = typeAnnotations.size();
    return size == 0 ? PsiAnnotation.EMPTY_ARRAY : typeAnnotations.toArray(new PsiAnnotation[size]);
  }

  public static void addTypeUseAnnotationsFromModifierList(PsiElement member, List<PsiAnnotation> typeAnnotations) {
    if (!(member instanceof PsiModifierListOwner)) return;
    PsiModifierList list = ((PsiModifierListOwner)member).getModifierList();
    PsiAnnotation[] gluedAnnotations = list == null ? PsiAnnotation.EMPTY_ARRAY : list.getAnnotations();
    for (PsiAnnotation anno : gluedAnnotations) {
      if (AnnotationsHighlightUtil.isAnnotationApplicableTo(anno, false, "TYPE_USE")) {
        typeAnnotations.add(anno);
      }
    }
  }

  public PsiType getDetachedType(@NotNull PsiElement context) {
    PatchedSoftReference<PsiType> cached = myCachedDetachedType;
    PsiType type = cached == null ? null : cached.get();
    if (type != null) return type;
    try {
      String combinedAnnotations = getCombinedAnnotationsText();
      String text = combinedAnnotations.length() == 0 ? getText().trim() : combinedAnnotations + " " + getText().trim();
      type = JavaPsiFacade.getInstance(getProject()).getElementFactory().createTypeFromText(text, context);
      myCachedDetachedType = new PatchedSoftReference<PsiType>(type);
    }
    catch (IncorrectOperationException e) {
      return getType();
    }
    return type;
  }

  @NotNull
  private String getCombinedAnnotationsText() {
    final boolean typeAnnotationsSupported = PsiUtil.isLanguageLevel8OrHigher(this);
    if (!typeAnnotationsSupported) return "";
    return StringUtil.join(getApplicableAnnotations(), ANNOTATION_TEXT, " ");
  }

  private static final Function<PsiAnnotation, String> ANNOTATION_TEXT = new Function<PsiAnnotation, String>() {
    public String fun(PsiAnnotation psiAnnotation) {
      return psiAnnotation.getText();
    }
  };

  public PsiType getTypeNoResolve(@NotNull PsiElement context) {
    PsiFile file = getContainingFile();
    String text;
    if (PsiUtil.isLanguageLevel8OrHigher(file)) {
      String combinedAnnotations = StringUtil.join(getAnnotations(), ANNOTATION_TEXT, " ");
      text = combinedAnnotations.length() == 0 ? getText().trim() : combinedAnnotations + " " + getText().trim();
    }
    else {
      text = getText().trim();
    }
    try {
      return JavaPsiFacade.getInstance(getProject()).getElementFactory().createTypeFromText(text, context);
    }
    catch (IncorrectOperationException e) {
      String s = "Parent: " + DebugUtil.psiToString(getParent(), false);
      s += "Context: " + DebugUtil.psiToString(context, false);
      LOG.error(s,e);
      return null;
    }
  }

  @NotNull
  private PsiType createWildcardType() {
    final PsiType temp;
    if (getFirstChildNode().getTreeNext() == null) {
      temp = PsiWildcardType.createUnbounded(getManager());
    }
    else if (getLastChildNode().getElementType() == JavaElementType.TYPE) {
      PsiTypeElement bound = SourceTreeToPsiMap.treeToPsiNotNull(getLastChildNode());
      ASTNode keyword = getFirstChildNode();
      while (keyword != null &&
             keyword.getElementType() != JavaTokenType.EXTENDS_KEYWORD &&
             keyword.getElementType() != JavaTokenType.SUPER_KEYWORD) {
        keyword = keyword.getTreeNext();
      }
      if (keyword != null) {
        IElementType i = keyword.getElementType();
        if (i == JavaTokenType.EXTENDS_KEYWORD) {
          temp = PsiWildcardType.createExtends(getManager(), bound.getType());
        }
        else if (i == JavaTokenType.SUPER_KEYWORD) {
          temp = PsiWildcardType.createSuper(getManager(), bound.getType());
        }
        else {
          LOG.assertTrue(false);
          temp = PsiWildcardType.createUnbounded(getManager());
        }
      }
      else {
        temp = PsiWildcardType.createUnbounded(getManager());
      }
    }
    else {
      temp = PsiWildcardType.createUnbounded(getManager());
    }
    return temp;
  }

  public PsiJavaCodeReferenceElement getInnermostComponentReferenceElement() {
    TreeElement firstChildNode = getFirstChildNode();
    if (firstChildNode == null) return null;
    if (firstChildNode.getElementType() == JavaElementType.TYPE) {
      return SourceTreeToPsiMap.<PsiTypeElement>treeToPsiNotNull(firstChildNode).getInnermostComponentReferenceElement();
    }
    else {
      return getReferenceElement();
    }
  }

  public PsiAnnotationOwner getOwner(PsiAnnotation annotation) {
    PsiElement next = PsiTreeUtil.skipSiblingsForward(annotation, PsiComment.class, PsiWhiteSpace.class);
    if (next != null && next.getNode().getElementType() == JavaTokenType.LBRACKET) {
      return getType();  // annotation belongs to array type dimension
    }
    return this;
  }

  @Nullable
  private PsiJavaCodeReferenceElement getReferenceElement() {
    ASTNode ref = findChildByType(JavaElementType.JAVA_CODE_REFERENCE);
    if (ref == null) return null;
    return (PsiJavaCodeReferenceElement)SourceTreeToPsiMap.treeElementToPsi(ref);
  }

  public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull ResolveState state, PsiElement lastParent, @NotNull PsiElement place){
    processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, this);
    return true;
  }

  @NotNull
  public PsiAnnotation[] getAnnotations() {
    List<PsiAnnotation> result = null;
    for (ASTNode child = getFirstChildNode(); child != null; child = child.getTreeNext()) {
      if (child.getElementType() != JavaElementType.ANNOTATION) continue;
      ASTNode next = TreeUtil.skipElements(child.getTreeNext(), ElementType.JAVA_COMMENT_OR_WHITESPACE_BIT_SET);
      if (next != null && next.getElementType() == JavaTokenType.LBRACKET) continue; //annotation on array dimension
      if (result == null) result = new SmartList<PsiAnnotation>();
      PsiElement element = child.getPsi();
      assert element != null;
      result.add((PsiAnnotation)element);
    }

    return result== null ?  PsiAnnotation.EMPTY_ARRAY : toAnnotationsArray(result);
  }

  @NotNull
  public PsiAnnotation[] getApplicableAnnotations() {
    PsiAnnotation[] annotations = getAnnotations();

    ArrayList<PsiAnnotation> list = new ArrayList<PsiAnnotation>(Arrays.asList(annotations));
    addTypeUseAnnotationsFromModifierList(getParent(), list);

    return toAnnotationsArray(list);
  }

  public PsiAnnotation findAnnotation(@NotNull @NonNls String qualifiedName) {
    return PsiImplUtil.findAnnotation(this, qualifiedName);
  }

  @NotNull
  public PsiAnnotation addAnnotation(@NotNull @NonNls String qualifiedName) {
    throw new UnsupportedOperationException();//todo
  }

  @Override
  public PsiElement replace(@NotNull PsiElement newElement) throws IncorrectOperationException {
    PsiElement result = super.replace(newElement);

    // We want to reformat method call arguments on method return type change because there is a possible situation that they are aligned
    // and the change breaks the alignment.
    // Example:
    //     Object test(1,
    //                 2) {}
    // Suppose we're changing return type to 'MyCustomClass'. We get the following if parameter list is not reformatted:
    //     MyCustomClass test(1,
    //                 2) {}
    PsiElement parent = result.getParent();
    if (parent instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)parent;
      CodeEditUtil.markToReformat(method.getParameterList().getNode(), true);
    }

    // We cover situation like below here:
    //     int test(int i, int j) {}
    //     ...
    //     int i = test(1,
    //                  2);
    // I.e. the point is to avoid code like below during changing 'test()' return type from 'int' to 'long':
    //     long i = test(1,
    //                  2);
    else if (parent instanceof PsiVariable) {
      PsiVariable variable = (PsiVariable)parent;
      if (variable.hasInitializer()) {
        PsiExpression methodCallCandidate = variable.getInitializer();
        if (methodCallCandidate instanceof PsiMethodCallExpression) {
          PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)methodCallCandidate;
          CodeEditUtil.markToReformat(methodCallExpression.getArgumentList().getNode(), true);
        }
      }
    }

    return result;
  }
}

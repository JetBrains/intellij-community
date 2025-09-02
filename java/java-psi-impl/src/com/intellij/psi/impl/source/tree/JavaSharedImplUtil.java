// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree;

import com.intellij.codeInsight.AnnotationTargetUtil;
import com.intellij.lang.ASTFactory;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.GeneratedMarkerVisitor;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.cache.TypeInfo;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.impl.source.tree.java.AnnotationElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.CharTable;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class JavaSharedImplUtil {
  private static final Logger LOG = Logger.getInstance(JavaSharedImplUtil.class);

  private static final TokenSet BRACKETS = TokenSet.create(JavaTokenType.LBRACKET, JavaTokenType.RBRACKET);

  private JavaSharedImplUtil() { }

  public static PsiType getType(@NotNull PsiTypeElement typeElement, @NotNull PsiElement anchor) {
    return getType(typeElement, anchor, null);
  }

  public static PsiType getType(@NotNull PsiTypeElement typeElement, @NotNull PsiElement anchor, @Nullable PsiAnnotation stopAt) {
    PsiType type = typeElement.getType();
    boolean ellipsisType = type instanceof PsiEllipsisType;

    List<PsiAnnotation[]> allAnnotations = collectAnnotations(anchor, stopAt);
    if (allAnnotations == null) return null;
    for (int i = 0, size = allAnnotations.size(); i < size; i++) {
      type = ((ellipsisType && i == size - 1) ? new PsiEllipsisType(type) : type.createArrayType())
        .annotate(TypeAnnotationProvider.Static.create(allAnnotations.get(i)));
    }

    return type;
  }

  /**
   * Collects annotations bound to C-style arrays.
   */
  private static @Nullable List<PsiAnnotation[]> collectAnnotations(@NotNull PsiElement anchor, @Nullable PsiAnnotation stopAt) {
    List<PsiAnnotation[]> annotations = Collections.emptyList();

    List<PsiAnnotation> current = null;
    boolean found = stopAt == null;
    boolean stop = false;
    for (PsiElement child = anchor.getNextSibling(); child != null; child = child.getNextSibling()) {
      if (child instanceof PsiComment || child instanceof PsiWhiteSpace) continue;

      if (child instanceof PsiAnnotation) {
        if (current == null) current = new SmartList<>();
        current.add((PsiAnnotation)child);
        if (child == stopAt) found = stop = true;
        continue;
      }

      if (PsiUtil.isJavaToken(child, JavaTokenType.LBRACKET)) {
        if (annotations == Collections.EMPTY_LIST) annotations = new SmartList<>();
        annotations.add(0, current == null ? PsiAnnotation.EMPTY_ARRAY : ContainerUtil.toArray(current, PsiAnnotation.ARRAY_FACTORY));
        current = null;
        if (stop) return annotations;
      }
      else if (!PsiUtil.isJavaToken(child, JavaTokenType.RBRACKET)) {
        break;
      }
    }

    // annotation is misplaced (either located before the anchor or has no following brackets)
    return !found || stop ? null : annotations;
  }

  public static @NotNull PsiType createTypeFromStub(@NotNull PsiModifierListOwner owner, @NotNull TypeInfo typeInfo) {
    String typeText = typeInfo.annotatedText();
    PsiType type = JavaPsiFacade.getInstance(owner.getProject()).getParserFacade().createTypeFromText(typeText, owner);
    return applyAnnotations(type, owner.getModifierList());
  }

  public static @NotNull PsiType applyAnnotations(@NotNull PsiType type, @Nullable PsiModifierList modifierList) {
    if (modifierList != null) {
      PsiAnnotation[] annotations = modifierList.getAnnotations();
      if (annotations.length > 0) {
        if (type instanceof PsiArrayType) {
          Deque<PsiArrayType> types = new ArrayDeque<>();
          do {
            types.push((PsiArrayType)type);
            type = ((PsiArrayType)type).getComponentType();
          }
          while (type instanceof PsiArrayType);
          type = annotate(type, modifierList, annotations);
          while (!types.isEmpty()) {
            PsiArrayType t = types.pop();
            type = t instanceof PsiEllipsisType ? new PsiEllipsisType(type, t.getAnnotations()) : new PsiArrayType(type, t.getAnnotations());
          }
          return type;
        }
        else if (type instanceof PsiDisjunctionType) {
          List<PsiType> components = new ArrayList<>(((PsiDisjunctionType)type).getDisjunctions());
          components.set(0, annotate(components.get(0), modifierList, annotations));
          return ((PsiDisjunctionType)type).newDisjunctionType(components);
        }
        else {
          return annotate(type, modifierList, annotations);
        }
      }
    }

    return type;
  }

  private static @NotNull PsiType annotate(@NotNull PsiType type, @NotNull PsiModifierList modifierList, PsiAnnotation @NotNull [] annotations) {
    TypeAnnotationProvider original =
      modifierList.getParent() instanceof PsiMethod ? type.getAnnotationProvider() : TypeAnnotationProvider.EMPTY;
    TypeAnnotationProvider provider = new FilteringTypeAnnotationProvider(annotations, original);
    return type.annotate(provider);
  }

  /**
   * Normalizes brackets, i.e. any brackets after the identifier of the variable are moved to the correct
   * position in the type element of the variable.
   * For example <br>
   * {@code String @One [] array @Two [] = null;}<br>
   * becomes<br>
   * {@code String @Two [] @One [] array = null;}.<br>
   * See <a href ="https://docs.oracle.com/javase/specs/jls/se20/html/jls-10.html#jls-10.2">JLS 10.2</a>
   *
   * @param variable  an array variable.
   */
  public static void normalizeBrackets(@NotNull PsiVariable variable) {
    CompositeElement variableElement = (CompositeElement)variable.getNode();

    PsiTypeElement typeElement = variable.getTypeElement();
    PsiIdentifier nameElement = variable.getNameIdentifier();
    LOG.assertTrue(typeElement != null && nameElement != null);

    ASTNode type = typeElement.getNode();
    ASTNode name = nameElement.getNode();

    ASTNode firstBracket = null;
    ASTNode lastBracket = null;
    int arrayCount = 0;
    ASTNode element = name;
    MultiMap<Integer, AnnotationElement> annotationElementsToMove = new MultiMap<>();
    while (element != null) {
      element = PsiImplUtil.skipWhitespaceAndComments(element.getTreeNext());
      if (element instanceof AnnotationElement) {
        annotationElementsToMove.putValue(arrayCount, (AnnotationElement)element);
        continue;
      }
      if (element == null || element.getElementType() != JavaTokenType.LBRACKET) break;
      if (firstBracket == null) firstBracket = element;
      lastBracket = element;
      arrayCount++;

      element = PsiImplUtil.skipWhitespaceAndComments(element.getTreeNext());
      if (element == null || element.getElementType() != JavaTokenType.RBRACKET) break;
      lastBracket = element;
    }

    if (firstBracket != null) {
      element = PsiImplUtil.skipWhitespaceAndComments(name.getTreeNext());
      while (element != null) {
        ASTNode next = PsiImplUtil.skipWhitespaceAndComments(element.getTreeNext());
        CodeEditUtil.removeChild(variableElement, element);
        if (element == lastBracket) break;
        element = next;
      }

      CompositeElement newType = (CompositeElement)type.clone();
      if (!(typeElement.getType() instanceof PsiArrayType)) {
        CompositeElement newType1 = ASTFactory.composite(JavaElementType.TYPE);
        newType1.rawAddChildren(newType);
        newType = newType1;
      }
      for (int i = arrayCount - 1; i >= 0; i--) {
        // add in reverse so the anchor can remain the same
        TreeElement anchor = newType.getFirstChildNode();
        anchor.rawInsertAfterMe(ASTFactory.leaf(JavaTokenType.RBRACKET, "]"));
        anchor.rawInsertAfterMe(ASTFactory.leaf(JavaTokenType.LBRACKET, "["));
        List<AnnotationElement> annotations = (List<AnnotationElement>)annotationElementsToMove.get(i);
        for (int j = annotations.size() - 1; j >= 0; j--) {
          anchor.rawInsertAfterMe(annotations.get(j));
        }
      }
      newType.acceptTree(new GeneratedMarkerVisitor());
      newType.putUserData(CharTable.CHAR_TABLE_KEY, SharedImplUtil.findCharTableByTree(type));
      CodeEditUtil.replaceChild(variableElement, type, newType);
    }
  }

  public static @NotNull PsiElement getPatternVariableDeclarationScope(@NotNull PsiPatternVariable variable) {
    PsiElement parent = variable.getPattern().getParent();
    if (!(parent instanceof PsiInstanceOfExpression) && !(parent instanceof PsiCaseLabelElementList) && !(parent instanceof PsiPattern)
        && !(parent instanceof PsiDeconstructionList)) {
      return parent;
    }
    return getInstanceOfPartDeclarationScope(parent);
  }

  public static @Nullable PsiElement getPatternVariableDeclarationScope(@NotNull PsiInstanceOfExpression instanceOfExpression) {
    return getInstanceOfPartDeclarationScope(instanceOfExpression);
  }

  private static PsiElement getInstanceOfPartDeclarationScope(@NotNull PsiElement parent) {
    boolean negated = false;
    for (PsiElement nextParent = parent.getParent(); ; parent = nextParent, nextParent = parent.getParent()) {
      if (nextParent instanceof PsiParenthesizedExpression) continue;
      if (nextParent instanceof PsiForeachStatementBase ||
        nextParent instanceof PsiConditionalExpression && parent == ((PsiConditionalExpression)nextParent).getCondition()) {
        return nextParent;
      }
      if (nextParent instanceof PsiPrefixExpression &&
          ((PsiPrefixExpression)nextParent).getOperationTokenType().equals(JavaTokenType.EXCL)) {
        negated = !negated;
        continue;
      }
      if (nextParent instanceof PsiPolyadicExpression) {
        IElementType tokenType = ((PsiPolyadicExpression)nextParent).getOperationTokenType();
        if (tokenType.equals(JavaTokenType.ANDAND) && !negated || tokenType.equals(JavaTokenType.OROR) && negated) continue;
      }
      if (nextParent instanceof PsiIfStatement) {
        while (nextParent.getParent() instanceof PsiLabeledStatement) {
          nextParent = nextParent.getParent();
        }
        return nextParent.getParent();
      }
      if (nextParent instanceof PsiConditionalLoopStatement) {
        if (!negated) return nextParent;
        while (nextParent.getParent() instanceof PsiLabeledStatement) {
          nextParent = nextParent.getParent();
        }
        return nextParent.getParent();
      }
      if (nextParent instanceof PsiSwitchLabelStatementBase) {
        while (nextParent.getParent() instanceof PsiLabeledStatement) {
          nextParent = nextParent.getParent();
        }
        return nextParent.getParent();
      }
      if (nextParent instanceof PsiPattern || nextParent instanceof PsiCaseLabelElementList ||
          (parent instanceof PsiPattern && nextParent instanceof PsiInstanceOfExpression) ||
          (parent instanceof PsiPattern && nextParent instanceof PsiDeconstructionList)) {
        continue;
      }
      return parent;
    }
  }

  public static void setInitializer(@NotNull PsiVariable variable, PsiExpression initializer) throws IncorrectOperationException {
    PsiExpression oldInitializer = variable.getInitializer();
    if (oldInitializer != null) {
      oldInitializer.delete();
    }
    if (initializer == null) {
      return;
    }
    CompositeElement variableElement = (CompositeElement)variable.getNode();
    ASTNode eq = variableElement.findChildByRole(ChildRole.INITIALIZER_EQ);
    if (eq == null) {
      final CharTable charTable = SharedImplUtil.findCharTableByTree(variableElement);
      eq = Factory.createSingleLeafElement(JavaTokenType.EQ, "=", 0, 1, charTable, variable.getManager());
      PsiElement identifier = variable.getNameIdentifier();
      assert identifier != null : variable;
      ASTNode node = PsiImplUtil.skipWhitespaceCommentsAndTokens(identifier.getNode().getTreeNext(), BRACKETS);
      variableElement.addInternal((TreeElement)eq, eq, node, Boolean.TRUE);
      eq = variableElement.findChildByRole(ChildRole.INITIALIZER_EQ);
      assert eq != null : variable;
    }
    variable.addAfter(initializer, eq.getPsi());
  }

  private static final class FilteringTypeAnnotationProvider implements TypeAnnotationProvider {
    private final PsiAnnotation[] myCandidates;
    private final TypeAnnotationProvider myOriginalProvider;
    private volatile PsiAnnotation[] myCache;

    private FilteringTypeAnnotationProvider(PsiAnnotation @NotNull [] candidates, @NotNull TypeAnnotationProvider originalProvider) {
      myCandidates = candidates;
      myOriginalProvider = originalProvider;
    }

    @Override
    public PsiAnnotation @NotNull [] getAnnotations() {
      PsiAnnotation[] result = myCache;
      if (result == null) {
        List<PsiAnnotation> filtered = JBIterable.of(myCandidates)
          .filter(annotation ->
                    !annotation.isValid() || // avoid exceptions in the next line, enable isValid checks at more specific call sites
                    AnnotationTargetUtil.isTypeAnnotation(annotation))
          .append(myOriginalProvider.getAnnotations())
          .toList();
        myCache = result = filtered.isEmpty() ? PsiAnnotation.EMPTY_ARRAY : filtered.toArray(PsiAnnotation.EMPTY_ARRAY);
      }
      return result;
    }
  }
}
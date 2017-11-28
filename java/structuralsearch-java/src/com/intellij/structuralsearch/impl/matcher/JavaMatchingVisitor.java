// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.impl.matcher;

import com.intellij.dupLocator.iterators.ArrayBackedNodeIterator;
import com.intellij.dupLocator.iterators.NodeIterator;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.structuralsearch.MatchOptions;
import com.intellij.structuralsearch.MatchResult;
import com.intellij.structuralsearch.impl.matcher.handlers.MatchingHandler;
import com.intellij.structuralsearch.impl.matcher.handlers.SubstitutionHandler;
import com.intellij.structuralsearch.impl.matcher.iterators.DocValuesIterator;
import com.intellij.structuralsearch.impl.matcher.iterators.HierarchyNodeIterator;
import com.intellij.structuralsearch.impl.matcher.predicates.MatchPredicate;
import com.intellij.structuralsearch.impl.matcher.predicates.NotPredicate;
import com.intellij.structuralsearch.impl.matcher.predicates.RegExpPredicate;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author Eugene.Kudelevsky
 */
public class JavaMatchingVisitor extends JavaElementVisitor {
  public static final String[] MODIFIERS = {
    PsiModifier.PUBLIC, PsiModifier.PROTECTED, PsiModifier.PRIVATE, PsiModifier.STATIC, PsiModifier.ABSTRACT, PsiModifier.FINAL,
    PsiModifier.NATIVE, PsiModifier.SYNCHRONIZED, PsiModifier.STRICTFP, PsiModifier.TRANSIENT, PsiModifier.VOLATILE, PsiModifier.DEFAULT
  };
  private final GlobalMatchingVisitor myMatchingVisitor;
  private PsiClass myClazz;

  static {
    Arrays.sort(MODIFIERS);
  }

  public JavaMatchingVisitor(GlobalMatchingVisitor matchingVisitor) {
    this.myMatchingVisitor = matchingVisitor;
  }

  @Override
  public void visitComment(PsiComment comment) {
    PsiComment comment2 = null;

    if (!(myMatchingVisitor.getElement() instanceof PsiComment)) {
      if (myMatchingVisitor.getElement() instanceof PsiMember) {
        final PsiElement[] children = myMatchingVisitor.getElement().getChildren();
        if (children[0] instanceof PsiComment) {
          comment2 = (PsiComment)children[0];
        }
      }
    }
    else {
      comment2 = (PsiComment)myMatchingVisitor.getElement();
    }

    if (comment2 == null) {
      myMatchingVisitor.setResult(false);
      return;
    }

    final Object userData = comment.getUserData(CompiledPattern.HANDLER_KEY);

    if (userData instanceof String) {
      final String str = (String)userData;
      int end = comment2.getTextLength();

      if (comment2.getTokenType() == JavaTokenType.C_STYLE_COMMENT) {
        end -= 2;
      }
      myMatchingVisitor.setResult(((SubstitutionHandler)myMatchingVisitor.getMatchContext().getPattern().getHandler(str)).handle(
        comment2,
        2,
        end,
        myMatchingVisitor.getMatchContext()
      ));
    }
    else if (userData instanceof MatchingHandler) {
      myMatchingVisitor.setResult(((MatchingHandler)userData).match(comment, comment2, myMatchingVisitor.getMatchContext()));
    }
    else {
      myMatchingVisitor.setResult(myMatchingVisitor.matchText(comment, comment2));
    }
  }

  @Override
  public final void visitModifierList(final PsiModifierList list) {
    final PsiModifierList list2 = (PsiModifierList)myMatchingVisitor.getElement();

    for (@PsiModifier.ModifierConstant String modifier : MODIFIERS) {
      if (list.hasModifierProperty(modifier) && !list2.hasModifierProperty(modifier)) {
        myMatchingVisitor.setResult(false);
        return;
      }
    }

    final PsiAnnotation[] annotations = list.getAnnotations();
    if (annotations.length > 0) {
      HashSet<PsiAnnotation> set = new HashSet<>(Arrays.asList(annotations));

      for (PsiAnnotation annotation : annotations) {
        final PsiJavaCodeReferenceElement nameReferenceElement = annotation.getNameReferenceElement();

        if (nameReferenceElement != null && MatchOptions.MODIFIER_ANNOTATION_NAME.equals(nameReferenceElement.getText())) {
          final PsiAnnotationParameterList parameterList = annotation.getParameterList();
          final PsiNameValuePair[] attributes = parameterList.getAttributes();

          for (PsiNameValuePair pair : attributes) {
            final PsiAnnotationMemberValue value = pair.getValue();
            if (value == null) continue;

            if (value instanceof PsiArrayInitializerMemberValue) {
              boolean matchedOne = false;

              for (PsiAnnotationMemberValue v : ((PsiArrayInitializerMemberValue)value).getInitializers()) {
                if (annotationValueMatchesModifierList(list2, v)) {
                  matchedOne = true;
                  break;
                }
              }

              if (!matchedOne) {
                myMatchingVisitor.setResult(false);
                return;
              }
            }
            else {
              if (!annotationValueMatchesModifierList(list2, value)) {
                myMatchingVisitor.setResult(false);
                return;
              }
            }
          }

          set.remove(annotation);
        }
      }

      myMatchingVisitor.setResult(set.isEmpty() ||
                                  myMatchingVisitor.matchInAnyOrder(set.toArray(new PsiAnnotation[set.size()]), list2.getAnnotations()));
    }
    else {
      myMatchingVisitor.setResult(true);
    }
  }

  private static boolean annotationValueMatchesModifierList(PsiModifierList list2, PsiAnnotationMemberValue value) {
    @PsiModifier.ModifierConstant final String name = StringUtil.unquoteString(value.getText());
    if (MatchOptions.INSTANCE_MODIFIER_NAME.equals(name)) {
      return !list2.hasModifierProperty(PsiModifier.STATIC) && !list2.hasModifierProperty(PsiModifier.ABSTRACT) &&
             list2.getParent() instanceof PsiMember;
    }
    return list2.hasModifierProperty(name) && (!PsiModifier.PACKAGE_LOCAL.equals(name) || list2.getParent() instanceof PsiMember);
  }

  @Override
  public void visitDocTag(final PsiDocTag tag) {
    final PsiDocTag tag2 = (PsiDocTag)myMatchingVisitor.getElement();
    final boolean isTypedVar = myMatchingVisitor.getMatchContext().getPattern().isTypedVar(tag.getNameElement());

    myMatchingVisitor.setResult(isTypedVar || tag.getName().equals(tag2.getName()));

    PsiElement psiDocTagValue = tag.getValueElement();
    boolean isTypedValue = false;

    if (myMatchingVisitor.getResult() && psiDocTagValue != null) {
      final PsiElement[] children = psiDocTagValue.getChildren();
      if (children.length == 1) {
        psiDocTagValue = children[0];
      }
      isTypedValue = myMatchingVisitor.getMatchContext().getPattern().isTypedVar(psiDocTagValue);

      if (isTypedValue) {
        if (tag2.getValueElement() != null) {
          myMatchingVisitor.setResult(myMatchingVisitor.handleTypedElement(psiDocTagValue, tag2.getValueElement()));
        }
        else {
          myMatchingVisitor.setResult(myMatchingVisitor.allowsAbsenceOfMatch(psiDocTagValue));
        }
      }
    }

    if (myMatchingVisitor.getResult() && !isTypedValue) {
      myMatchingVisitor.setResult(myMatchingVisitor.matchInAnyOrder(
        new DocValuesIterator(tag.getFirstChild()),
        new DocValuesIterator(tag2.getFirstChild())
      ));
    }

    if (myMatchingVisitor.getResult() && isTypedVar) {
      myMatchingVisitor.setResult(myMatchingVisitor.handleTypedElement(tag.getNameElement(), tag2.getNameElement()));
    }
  }

  @Override
  public void visitDocComment(final PsiDocComment comment) {
    PsiDocComment comment2;

    if (myMatchingVisitor.getElement() instanceof PsiDocCommentOwner) {
      comment2 = ((PsiDocCommentOwner)myMatchingVisitor.getElement()).getDocComment();

      if (comment2 == null) {
        // doc comment are not collapsed for inner classes!
        myMatchingVisitor.setResult(false);
        return;
      }
    }
    else {
      comment2 = (PsiDocComment)myMatchingVisitor.getElement();

      if (myMatchingVisitor.getElement().getParent() instanceof PsiDocCommentOwner) {
        myMatchingVisitor.setResult(false);
        return; // we should matched the doc before
      }
    }

    if (comment.getTags().length > 0) {
      myMatchingVisitor.setResult(myMatchingVisitor.matchInAnyOrder(comment.getTags(), comment2.getTags()));
    }
    else {
      visitComment(comment);
    }
  }

  @Override
  public void visitElement(PsiElement el) {
    myMatchingVisitor.setResult(myMatchingVisitor.matchText(el, myMatchingVisitor.getElement()));
  }

  @Override
  public void visitArrayInitializerExpression(PsiArrayInitializerExpression expression) {
    final PsiArrayInitializerExpression expr2 = (PsiArrayInitializerExpression)myMatchingVisitor.getElement();

    myMatchingVisitor.setResult(myMatchingVisitor.matchSequentially(
      new ArrayBackedNodeIterator(expression.getInitializers()),
      new ArrayBackedNodeIterator(expr2.getInitializers())
    ));
  }

  @Override
  public void visitClassInitializer(PsiClassInitializer initializer) {
    PsiClassInitializer initializer2 = (PsiClassInitializer)myMatchingVisitor.getElement();
    myMatchingVisitor.setResult(myMatchingVisitor.match(initializer.getModifierList(), initializer2.getModifierList()) &&
                                myMatchingVisitor.matchSons(initializer.getBody(), initializer2.getBody()));
  }

  @Override
  public void visitCodeBlock(PsiCodeBlock block) {
    myMatchingVisitor.setResult(myMatchingVisitor.matchSons(block, myMatchingVisitor.getElement()));
  }

  @Override
  public void visitJavaToken(final PsiJavaToken token) {
    PsiElement element = myMatchingVisitor.getElement();
    boolean result;

    if (!(element instanceof PsiJavaToken)) {
      result = myMatchingVisitor.matchText(token, element);
    } else {
      final PsiJavaToken anotherToken = (PsiJavaToken)element;

      result = token.getTokenType() == anotherToken.getTokenType() && myMatchingVisitor.matchText(token, anotherToken);
    }

    myMatchingVisitor.setResult(result);
  }

  @Override
  public void visitAnnotation(PsiAnnotation annotation) {
    final PsiAnnotation other = (PsiAnnotation)myMatchingVisitor.getElement();
    myMatchingVisitor.setResult(myMatchingVisitor.match(annotation.getNameReferenceElement(), other.getNameReferenceElement()) &&
                                myMatchingVisitor.matchInAnyOrder(annotation.getParameterList().getAttributes(),
                                                                  other.getParameterList().getAttributes()));
  }

  @Override
  public void visitNameValuePair(PsiNameValuePair pair) {
    final PsiNameValuePair elementNameValuePair = (PsiNameValuePair)myMatchingVisitor.getElement();

    final PsiAnnotationMemberValue value = pair.getValue();
    myMatchingVisitor.setResult(myMatchingVisitor.match(value, elementNameValuePair.getValue()));
    if (myMatchingVisitor.getResult()) {
      final PsiIdentifier nameIdentifier = pair.getNameIdentifier();
      if (nameIdentifier != null) {
        final PsiIdentifier otherIdentifier = elementNameValuePair.getNameIdentifier();
        final MatchingHandler handler = myMatchingVisitor.getMatchContext().getPattern().getHandler(nameIdentifier);
        if (handler instanceof SubstitutionHandler) {
          myMatchingVisitor.setResult(((SubstitutionHandler)handler).handle(otherIdentifier, myMatchingVisitor.getMatchContext()));
        }
        else {
          myMatchingVisitor.setResult(
            (PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME.equals(nameIdentifier.getText()) && otherIdentifier == null) ||
            myMatchingVisitor.match(nameIdentifier, otherIdentifier));
        }
      }
    }
  }

  @Override
  public void visitAnnotationArrayInitializer(PsiArrayInitializerMemberValue initializer) {
    final PsiArrayInitializerMemberValue otherInitializer = (PsiArrayInitializerMemberValue)myMatchingVisitor.getElement();
    final PsiAnnotationMemberValue[] initializers = initializer.getInitializers();
    myMatchingVisitor.setResult(myMatchingVisitor.matchSequentially(initializers, otherInitializer.getInitializers()));
  }

  private boolean checkHierarchy(PsiMember element, PsiMember patternElement) {
    final MatchingHandler handler = myMatchingVisitor.getMatchContext().getPattern().getHandler(patternElement);
    if (handler instanceof SubstitutionHandler) {
      final SubstitutionHandler handler2 = (SubstitutionHandler)handler;

      if (!handler2.isSubtype()) {
        if (handler2.isStrictSubtype()) {
          // check if element is declared not in current class  (in ancestors)
          return element.getContainingClass() != myClazz;
        }
      }
      else {
        return true;
      }
    }

    // check if element is declared in current class (not in ancestors)
    return myClazz == null || element.getContainingClass() == myClazz;
  }

  @Override
  public void visitField(PsiField psiField) {
    final PsiDocComment comment = psiField.getDocComment();
    final PsiField other = (PsiField)myMatchingVisitor.getElement();
    if (comment != null) {
      myMatchingVisitor.setResult(myMatchingVisitor.match(comment, other));
      if (!myMatchingVisitor.getResult()) return;
    }
    if (!checkHierarchy(other, psiField)) {
      myMatchingVisitor.setResult(false);
      return;
    }
    super.visitField(psiField);
  }

  @Override
  public void visitAnonymousClass(final PsiAnonymousClass clazz) {
    final PsiAnonymousClass clazz2 = (PsiAnonymousClass)myMatchingVisitor.getElement();
    final boolean isTypedVar = myMatchingVisitor.getMatchContext().getPattern().isTypedVar(clazz.getFirstChild());

    myMatchingVisitor.setResult((myMatchingVisitor.match(clazz.getBaseClassReference(), clazz2.getBaseClassReference()) || isTypedVar) &&
                                myMatchingVisitor.matchSons(clazz.getArgumentList(), clazz2.getArgumentList()) &&
                                compareClasses(clazz, clazz2));

    if (myMatchingVisitor.getResult() && isTypedVar) {
      myMatchingVisitor.setResult(myMatchingVisitor.handleTypedElement(clazz.getFirstChild(), clazz2.getFirstChild()));
    }
  }

  @Override
  public void visitLambdaExpression(PsiLambdaExpression expression) {
    final PsiElement other = myMatchingVisitor.getElement();
    if (other instanceof PsiLambdaExpression) {
      final PsiLambdaExpression expression2 = (PsiLambdaExpression)other;
      boolean result = true;
      final PsiParameterList parameterList1 = expression.getParameterList();
      if (parameterList1.getParametersCount() != 0) {
        result = myMatchingVisitor.matchSons(parameterList1, expression2.getParameterList());
      }
      final PsiElement body1 = getElementToMatch(expression.getBody());
      if (body1 != null && result) {
        result = myMatchingVisitor.matchSequentially(body1, getElementToMatch(expression2.getBody()));
      }
      myMatchingVisitor.setResult(result);
    }
    else {
      myMatchingVisitor.setResult(false);
    }
  }

  private static PsiElement getElementToMatch(PsiElement element) {
    if (element instanceof PsiCodeBlock) {
      element = PsiTreeUtil.getChildOfAnyType(element, PsiStatement.class, PsiComment.class);
    }
    if (element instanceof PsiExpressionStatement) {
      element = ((PsiExpressionStatement)element).getExpression();
    }
    if (element instanceof PsiReturnStatement) {
      element = ((PsiReturnStatement)element).getReturnValue();
    }
    return element;
  }

  private boolean matchInAnyOrder(final PsiReferenceList elements, final PsiReferenceList elements2) {
    if ((elements == null && myMatchingVisitor.isLeftLooseMatching()) ||
        elements == elements2 // null
      ) {
      return true;
    }

    return myMatchingVisitor.matchInAnyOrder(
      elements.getReferenceElements(),
      (elements2 != null) ? elements2.getReferenceElements() : PsiElement.EMPTY_ARRAY
    );
  }

  private boolean compareClasses(final PsiClass clazz, final PsiClass clazz2) {
    final PsiClass saveClazz = this.myClazz;
    this.myClazz = clazz2;
    final JavaCompiledPattern javaPattern = (JavaCompiledPattern)myMatchingVisitor.getMatchContext().getPattern();

    final Set<PsiElement> matchedElements = new THashSet<>();
    final MatchContext.MatchedElementsListener oldListener = myMatchingVisitor.getMatchContext().getMatchedElementsListener();
    myMatchingVisitor.getMatchContext().setMatchedElementsListener(es -> matchedElements.addAll(es));
    try {
      final boolean templateIsInterface = clazz.isInterface();
      if (templateIsInterface && !clazz2.isInterface()) return false;
      if (templateIsInterface && clazz.isAnnotationType() && !clazz2.isAnnotationType()) return false;
      if (clazz.isEnum() && !clazz2.isEnum()) return false;
      if (clazz instanceof PsiTypeParameter != clazz2 instanceof PsiTypeParameter) return false;

      if (!matchInAnyOrder(clazz.getExtendsList(), clazz2.getExtendsList())) {
        return false;
      }

      // check if implements is in extended classes implements
      final PsiReferenceList implementsList = clazz.getImplementsList();
      if (implementsList != null) {
        if (!matchInAnyOrder(implementsList, clazz2.getImplementsList())) {
          final PsiReferenceList anotherExtendsList = clazz2.getExtendsList();
          final PsiJavaCodeReferenceElement[] referenceElements = implementsList.getReferenceElements();

          boolean accepted = false;

          if (referenceElements.length > 0 && anotherExtendsList != null) {
            final HierarchyNodeIterator iterator = new HierarchyNodeIterator(clazz2, true, true, false);

            accepted = myMatchingVisitor.matchInAnyOrder(new ArrayBackedNodeIterator(referenceElements), iterator);
          }

          if (!accepted) return false;
        }
      }

      final PsiField[] fields = clazz.getFields();

      if (fields.length > 0) {
        final PsiField[] fields2 = javaPattern.isRequestsSuperFields() ?
                                   clazz2.getAllFields() :
                                   clazz2.getFields();

        if (!myMatchingVisitor.matchInAnyOrder(fields, fields2)) {
          return false;
        }
      }

      final PsiMethod[] methods = clazz.getMethods();

      if (methods.length > 0) {
        final PsiMethod[] methods2 = javaPattern.isRequestsSuperMethods() ?
                                     clazz2.getAllMethods() :
                                     clazz2.getMethods();

        if (!myMatchingVisitor.matchInAnyOrder(methods, methods2)) {
          return false;
        }
      }

      final PsiClass[] nestedClasses = clazz.getInnerClasses();

      if (nestedClasses.length > 0) {
        final PsiClass[] nestedClasses2 = javaPattern.isRequestsSuperInners() ?
                                          clazz2.getAllInnerClasses() :
                                          clazz2.getInnerClasses();

        if (!myMatchingVisitor.matchInAnyOrder(nestedClasses, nestedClasses2)) {
          return false;
        }
      }

      final PsiClassInitializer[] initializers = clazz.getInitializers();
      if (initializers.length > 0) {
        final PsiClassInitializer[] initializers2 = clazz2.getInitializers();

        if (!myMatchingVisitor.matchInAnyOrder(initializers, initializers2)) {
          return false;
        }
      }

      final List<PsiElement> unmatchedElements = new SmartList<>(PsiTreeUtil.getChildrenOfTypeAsList(clazz2, PsiMember.class));
      unmatchedElements.removeAll(matchedElements);
      MatchingHandler unmatchedSubstitutionHandler = null;
      for (PsiElement element = clazz.getFirstChild(); element != null; element = element.getNextSibling()) {
        if (element instanceof PsiTypeElement && element.getNextSibling() instanceof PsiErrorElement) {
          unmatchedSubstitutionHandler = myMatchingVisitor.getMatchContext().getPattern().getHandler(element);
          break;
        }
      }
      if (unmatchedSubstitutionHandler instanceof SubstitutionHandler) {
        final SubstitutionHandler handler = (SubstitutionHandler)unmatchedSubstitutionHandler;
        for (PsiElement element : unmatchedElements) {
          handler.handle(element, myMatchingVisitor.getMatchContext());
        }
      } else {
        clazz2.putUserData(GlobalMatchingVisitor.UNMATCHED_ELEMENTS_KEY, unmatchedElements);
      }

      return true;
    }
    finally {
      this.myClazz = saveClazz;
      myMatchingVisitor.getMatchContext().setMatchedElementsListener(oldListener);
    }
  }

  private boolean matchBody(PsiElement patternElement, PsiElement matchElement) {
    if (myMatchingVisitor.getMatchContext().getOptions().isLooseMatching()) {
      if (matchElement instanceof PsiBlockStatement) {
        final PsiCodeBlock codeBlock = ((PsiBlockStatement)matchElement).getCodeBlock();
        if (patternElement instanceof PsiBlockStatement || codeBlock.getStatements().length == 1) {
          matchElement = codeBlock.getFirstChild();
        }
      }
      if (patternElement instanceof PsiBlockStatement) {
        patternElement = ((PsiBlockStatement)patternElement).getCodeBlock().getFirstChild();
      }
    }

    return myMatchingVisitor.matchSequentially(patternElement, matchElement);
  }

  @Override
  public void visitArrayAccessExpression(final PsiArrayAccessExpression slice) {
    final PsiElement other = myMatchingVisitor.getElement();
    if (other instanceof PsiArrayAccessExpression) {
      final PsiArrayAccessExpression slice2 = (PsiArrayAccessExpression)other;
      myMatchingVisitor.setResult(myMatchingVisitor.match(slice.getArrayExpression(), slice2.getArrayExpression()) &&
                                  myMatchingVisitor.match(slice.getIndexExpression(), slice2.getIndexExpression()));
    }
    else {
      myMatchingVisitor.setResult(false);
    }
  }

  @Override
  public void visitMethodReferenceExpression(PsiMethodReferenceExpression expression) {
    final PsiElement element = myMatchingVisitor.getElement();
    if (!(element instanceof PsiMethodReferenceExpression)) {
      myMatchingVisitor.setResult(false);
      return;
    }
    super.visitMethodReferenceExpression(expression);
  }

  @Override
  public void visitReferenceExpression(final PsiReferenceExpression reference) {
    final MatchContext context = myMatchingVisitor.getMatchContext();
    final CompiledPattern pattern = context.getPattern();
    MatchingHandler _handler = pattern.getHandlerSimple(reference.getReferenceNameElement());
    boolean special = false;
    if (_handler == null) {
      _handler = pattern.getHandlerSimple(reference);
      special = true;
    }

    final PsiElement element = myMatchingVisitor.getElement();
    PsiElement other = element instanceof PsiExpression && context.getOptions().isLooseMatching() ?
                       PsiUtil.skipParenthesizedExprDown((PsiExpression)element) :
                       element;
    final PsiExpression qualifier = reference.getQualifierExpression();
    if (_handler instanceof SubstitutionHandler && (qualifier == null || special)) {
      final SubstitutionHandler handler = (SubstitutionHandler)_handler;
      if (handler.isSubtype() || handler.isStrictSubtype()) {
        myMatchingVisitor.setResult(checkMatchWithinHierarchy(other, handler, reference));
      }
      else {
        myMatchingVisitor.setResult(handler.handle(other, context));
      }
      return;
    }

    final boolean multiMatch = other != null && reference.getContainingFile() == other.getContainingFile();
    if (!(other instanceof PsiReferenceExpression)) {
      myMatchingVisitor.setResult(multiMatch && myMatchingVisitor.matchText(reference, other));
      return;
    }

    final PsiReferenceExpression reference2 = (PsiReferenceExpression)other;

    final PsiExpression qualifier2 = reference2.getQualifierExpression();
    if (multiMatch &&
        (qualifier == null || qualifier instanceof PsiThisExpression || qualifier instanceof PsiSuperExpression) &&
        (qualifier2 == null || qualifier2 instanceof PsiThisExpression || qualifier2 instanceof PsiSuperExpression)) {
      final PsiElement target = reference.resolve();
      if (target != null) {
        myMatchingVisitor.setResult(target == reference2.resolve());
        return;
      }
    }
    if (qualifier == null && qualifier2 == null) {
      myMatchingVisitor.setResult(myMatchingVisitor.matchText(reference.getReferenceNameElement(), reference2.getReferenceNameElement()));
      return;
    }

    // handle field selection
    if (!(other.getParent() instanceof PsiMethodCallExpression) && qualifier != null) {
      final PsiElement referenceElement = reference.getReferenceNameElement();
      final PsiElement referenceElement2 = reference2.getReferenceNameElement();

      if (pattern.isTypedVar(referenceElement)) {
        myMatchingVisitor.setResult(myMatchingVisitor.handleTypedElement(referenceElement, referenceElement2));
      }
      else {
        myMatchingVisitor.setResult(myMatchingVisitor.matchText(referenceElement, referenceElement2));
      }

      if (!myMatchingVisitor.getResult()) {
        return;
      }
      if (qualifier2 != null) {
        myMatchingVisitor.setResult(myMatchingVisitor.match(qualifier, qualifier2));
      }
      else {
        final PsiElement referencedElement = MatchUtils.getReferencedElement(other);
        if (referencedElement instanceof PsiField) {
          final PsiField field = (PsiField)referencedElement;
          if (qualifier instanceof PsiThisExpression) {
            myMatchingVisitor.setResult(!field.hasModifierProperty(PsiModifier.STATIC));
            return;
          }
        }
        final MatchingHandler handler = pattern.getHandler(qualifier);
        matchImplicitQualifier(handler, referencedElement, context);
      }

      return;
    }

    myMatchingVisitor.setResult(false);
  }

  private static int getArrayDimensions(final PsiElement element) {
    if (element == null) {
      return 0;
    }
    final PsiElement parent = element.getParent();
    if (parent instanceof PsiVariable) {
      final PsiVariable variable = (PsiVariable)parent;
      final PsiType type = variable.getType();
      return type.getArrayDimensions();
    }
    else if (parent instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)parent;
      final PsiType type = method.getReturnType();
      return (type == null) ? 0 : type.getArrayDimensions();
    }
    else if (element instanceof PsiTypeElement) {
      final PsiTypeElement typeElement = (PsiTypeElement)element;
      final PsiType type = typeElement.getType();
      return type.getArrayDimensions();
    }
    return 0;
  }

  private static PsiTypeElement getInnermostComponentTypeElement(PsiTypeElement typeElement) {
    PsiElement child = typeElement.getFirstChild();
    while (child instanceof PsiTypeElement) {
      typeElement = (PsiTypeElement)child;
      child = typeElement.getFirstChild();
    }
    return typeElement;
  }

  private static PsiElement getInnermostComponent(PsiElement element) {
    if (!(element instanceof PsiTypeElement)) {
      return element;
    }
    final PsiTypeElement typeElement = (PsiTypeElement)element;
    if (typeElement.getType() instanceof PsiDisjunctionType) {
      // getInnermostComponentReferenceElement() doesn't make sense for disjunction type
      return typeElement;
    }
    final PsiJavaCodeReferenceElement referenceElement = typeElement.getInnermostComponentReferenceElement();
    return (referenceElement != null) ? referenceElement : getInnermostComponentTypeElement(typeElement);
  }

  private void copyResults(final MatchResultImpl ourResult) {
    if (ourResult.hasSons()) {
      for (MatchResult son : ourResult.getAllSons()) {
        myMatchingVisitor.getMatchContext().getResult().addSon((MatchResultImpl)son);
      }
    }
  }
  private static PsiTypeElement[] getTypeParameters(PsiJavaCodeReferenceElement referenceElement, boolean replaceDiamondWithExplicitTypes) {
    final PsiReferenceParameterList referenceElementParameterList = referenceElement.getParameterList();
    if (referenceElementParameterList == null) {
      return null;
    }
    final PsiTypeElement[] typeParameterElements = referenceElementParameterList.getTypeParameterElements();
    if (typeParameterElements.length != 1 || !replaceDiamondWithExplicitTypes) {
      return typeParameterElements;
    }
    final PsiType type = typeParameterElements[0].getType();
    if (!(type instanceof PsiDiamondType)) {
      return typeParameterElements;
    }
    final PsiDiamondType diamondType = (PsiDiamondType)type;
    final PsiDiamondType.DiamondInferenceResult inferenceResult = diamondType.resolveInferredTypes();
    final StringBuilder text = new StringBuilder(referenceElement.getQualifiedName());
    text.append('<');
    boolean comma = false;
    for (PsiType inferredType : inferenceResult.getInferredTypes()) {
      if (comma) {
        text.append(',');
      }
      else {
        comma = true;
      }
      text.append(inferredType.getCanonicalText());
    }
    text.append('>');
    final PsiJavaCodeReferenceElement newReferenceElement =
      JavaPsiFacade.getElementFactory(referenceElement.getProject()).createReferenceFromText(text.toString(), referenceElement);
    final PsiReferenceParameterList newParameterList = newReferenceElement.getParameterList();
    return newParameterList == null ? null : newParameterList.getTypeParameterElements();
  }

  private static boolean hasDiamondTypeParameter(PsiElement element) {
    if (!(element instanceof PsiJavaCodeReferenceElement)) {
      return false;
    }
    final PsiJavaCodeReferenceElement javaCodeReferenceElement = (PsiJavaCodeReferenceElement)element;
    final PsiReferenceParameterList parameterList = javaCodeReferenceElement.getParameterList();
    if (parameterList == null) {
      return false;
    }
    final PsiTypeElement[] elements = parameterList.getTypeParameterElements();
    return elements.length == 1 && elements[0].getType() instanceof PsiDiamondType;
  }

  private boolean matchType(final PsiElement patternType, final PsiElement matchedType) {
    PsiElement patternElement = getInnermostComponent(patternType);
    PsiElement matchedElement = getInnermostComponent(matchedType);

    PsiElement[] typeParameters = null;
    if (matchedElement instanceof PsiJavaCodeReferenceElement) {
      final PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement)matchedElement;
      typeParameters = getTypeParameters(referenceElement, !hasDiamondTypeParameter(patternElement));
    }
    else if (matchedElement instanceof PsiTypeParameter) {
      matchedElement = ((PsiTypeParameter)matchedElement).getNameIdentifier();
    }
    else if (matchedElement instanceof PsiClass && ((PsiClass)matchedElement).hasTypeParameters()) {
      typeParameters = ((PsiClass)matchedElement).getTypeParameters();
      matchedElement = ((PsiClass)matchedElement).getNameIdentifier();
    }
    else if (matchedElement instanceof PsiMethod && ((PsiMethod)matchedElement).hasTypeParameters()) {
      typeParameters = ((PsiMethod)matchedType).getTypeParameters();
      matchedElement = ((PsiMethod)matchedType).getNameIdentifier();
    }

    if (patternElement instanceof PsiJavaCodeReferenceElement) {
      final PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement)patternElement;
      final PsiReferenceParameterList list = referenceElement.getParameterList();
      if (list != null) {
        final PsiTypeElement[] elements = list.getTypeParameterElements();
        if (elements.length > 0 && (typeParameters == null || !myMatchingVisitor.matchSequentially(elements, typeParameters))) {
          return false;
        }
      }
      patternElement = referenceElement.getReferenceNameElement();
    }

    final int matchedArrayDimensions = getArrayDimensions(matchedType);
    final int patternArrayDimensions = getArrayDimensions(patternType);

    if (myMatchingVisitor.getMatchContext().getPattern().isTypedVar(patternElement)) {
      final SubstitutionHandler handler = (SubstitutionHandler)myMatchingVisitor.getMatchContext().getPattern().getHandler(patternElement);

      RegExpPredicate regExpPredicate = null;

      if (patternArrayDimensions != 0) {
        if (patternArrayDimensions != matchedArrayDimensions) {
          return false;
        }
      }
      else if (matchedArrayDimensions != 0) {
        regExpPredicate = handler.findRegExpPredicate();

        if (regExpPredicate != null) {
          regExpPredicate.setNodeTextGenerator(new RegExpPredicate.NodeTextGenerator() {
            public String getText(PsiElement element) {
              StringBuilder builder = new StringBuilder(RegExpPredicate.getMeaningfulText(element));
              for (int i = 0; i < matchedArrayDimensions; ++i) builder.append("[]");
              return builder.toString();
            }
          });
        }
      }

      try {
        if (handler.isSubtype() || handler.isStrictSubtype()) {
          return checkMatchWithinHierarchy(matchedElement, handler, patternElement);
        }
        else {
          return handler.handle(matchedElement, myMatchingVisitor.getMatchContext());
        }
      }
      finally {
        if (regExpPredicate != null) regExpPredicate.setNodeTextGenerator(null);
      }
    }

    if (matchedArrayDimensions != patternArrayDimensions) {
      return false;
    }

    if (patternElement instanceof PsiIdentifier) {
      final PsiElement parent = patternElement.getParent();
      if (parent instanceof PsiJavaCodeReferenceElement) {
        patternElement = parent;
      }
    }
    if (matchedElement instanceof PsiIdentifier) {
      final PsiElement parent = matchedElement.getParent();
      if (parent instanceof PsiJavaCodeReferenceElement) {
        matchedElement = parent;
      }
    }
    final String text = getText(patternElement);
    final String text2 = getText(matchedElement);
    final boolean caseSensitive = myMatchingVisitor.getMatchContext().getOptions().isCaseSensitiveMatch();
    final boolean equalsIgnorePackage = MatchUtils.compareWithNoDifferenceToPackage(text, text2, !caseSensitive);
    if (equalsIgnorePackage || !(matchedElement instanceof PsiJavaReference)) {
      return equalsIgnorePackage;
    }
    else {
      final PsiElement element2 = ((PsiJavaReference)matchedElement).resolve();

      if (!(element2 instanceof PsiClass)) {
        return false;
      }
      final String name = ((PsiClass)element2).getQualifiedName();
      return caseSensitive ? text.equals(name) : text.equalsIgnoreCase(name);
    }
  }

  @Contract(pure = true)
  private static String getText(@NotNull PsiElement element) {
    String result;
    if (element instanceof PsiClass) {
      result = ((PsiClass)element).getQualifiedName();
      if (result == null) result = element.getText();
    } else if (element instanceof PsiJavaCodeReferenceElement) {
      result = ((PsiJavaCodeReferenceElement)element).getCanonicalText();
    } else {
      result = element.getText();
    }
    final int index = result.indexOf('<');
    return index == -1 ? result : result.substring(0, index);
  }

  private boolean checkMatchWithinHierarchy(PsiElement el2, SubstitutionHandler handler, PsiElement context) {
    boolean includeInterfaces = true;
    boolean includeClasses = true;
    final PsiElement contextParent = context.getParent();

    if (contextParent instanceof PsiReferenceList) {
      final PsiElement grandParentContext = contextParent.getParent();

      if (grandParentContext instanceof PsiClass) {
        final PsiClass psiClass = (PsiClass)grandParentContext;

        if (contextParent == psiClass.getExtendsList()) {
          includeInterfaces = psiClass.isInterface();
        }
        else if (contextParent == psiClass.getImplementsList()) {
          includeClasses = false;
        }
      }
    }

    // is type2 is (strict) subtype of type
    final NodeIterator node = new HierarchyNodeIterator(el2, includeClasses, includeInterfaces);

    if (handler.isStrictSubtype()) {
      node.advance();
    }

    final boolean notPredicate = handler.getPredicate() instanceof NotPredicate;
    while (node.hasNext() && !handler.validate(node.current(), 0, -1, myMatchingVisitor.getMatchContext())) {
      if (notPredicate) return false;
      node.advance();
    }

    if (node.hasNext()) {
      handler.addResult(el2, 0, -1, myMatchingVisitor.getMatchContext());
      return true;
    }
    else {
      return false;
    }
  }

  @Override
  public void visitConditionalExpression(final PsiConditionalExpression cond) {
    final PsiConditionalExpression cond2 = (PsiConditionalExpression)myMatchingVisitor.getElement();

    myMatchingVisitor.setResult(myMatchingVisitor.match(cond.getCondition(), cond2.getCondition()) &&
                                myMatchingVisitor.matchSons(cond, cond2));
  }

  @Override
  public void visitPolyadicExpression(PsiPolyadicExpression expression) {
    final PsiPolyadicExpression expr2 = (PsiPolyadicExpression)myMatchingVisitor.getElement();

    myMatchingVisitor.setResult(expression.getOperationTokenType().equals(expr2.getOperationTokenType()));
    if (myMatchingVisitor.getResult()) {
      final PsiExpression[] operands1 = expression.getOperands();
      final PsiExpression[] operands2 = expr2.getOperands();
      myMatchingVisitor.setResult(
        myMatchingVisitor.matchSequentially(new ArrayBackedNodeIterator(operands1), new ArrayBackedNodeIterator(operands2)));
    }
  }

  @Override
  public void visitVariable(final PsiVariable var) {
    myMatchingVisitor.getMatchContext().pushResult();
    final PsiIdentifier nameIdentifier = var.getNameIdentifier();

    final boolean isTypedVar = myMatchingVisitor.getMatchContext().getPattern().isTypedVar(nameIdentifier);
    final PsiVariable var2 = (PsiVariable)myMatchingVisitor.getElement();

    try {
      myMatchingVisitor.setResult((myMatchingVisitor.matchText(var.getNameIdentifier(), var2.getNameIdentifier()) || isTypedVar) &&
                                   myMatchingVisitor.match(var.getModifierList(), var2.getModifierList()));
      if (myMatchingVisitor.getResult()) {
        final PsiTypeElement typeElement1 = var.getTypeElement();
        if (typeElement1 != null) {
          PsiTypeElement typeElement2 = var2.getTypeElement();
          if (typeElement2 == null) {
            typeElement2 = JavaPsiFacade.getElementFactory(var2.getProject()).createTypeElement(var2.getType());
          }
          myMatchingVisitor.setResult(myMatchingVisitor.match(typeElement1, typeElement2));
        }
      }

      if (myMatchingVisitor.getResult()) {
        // Check initializer
        final PsiExpression initializer = var.getInitializer();
        final PsiExpression var2Initializer = var2.getInitializer();
        myMatchingVisitor.setResult(myMatchingVisitor.match(initializer, var2Initializer));
      }
    }
    finally {
      saveOrDropResult(nameIdentifier, isTypedVar, var2.getNameIdentifier());
    }
  }

  private void matchArrayDims(final PsiNewExpression new1, final PsiNewExpression new2) {
    final PsiExpression[] arrayDims = new1.getArrayDimensions();
    final PsiExpression[] arrayDims2 = new2.getArrayDimensions();

    if (arrayDims.length == arrayDims2.length && arrayDims.length != 0) {
      for (int i = 0; i < arrayDims.length; ++i) {
        myMatchingVisitor.setResult(myMatchingVisitor.match(arrayDims[i], arrayDims2[i]));
        if (!myMatchingVisitor.getResult()) return;
      }
    }
    else {
      myMatchingVisitor.setResult((arrayDims == arrayDims2) && myMatchingVisitor.matchSons(new1.getArgumentList(), new2.getArgumentList()));
    }
  }

  private void saveOrDropResult(final PsiIdentifier methodNameNode, final boolean typedVar, final PsiIdentifier methodNameNode2) {
    MatchResultImpl ourResult = myMatchingVisitor.getMatchContext().hasResult() ? myMatchingVisitor.getMatchContext().getResult() : null;
    myMatchingVisitor.getMatchContext().popResult();

    if (myMatchingVisitor.getResult()) {
      if (typedVar) {
        final SubstitutionHandler handler =
          (SubstitutionHandler)myMatchingVisitor.getMatchContext().getPattern().getHandler(methodNameNode);
        if (ourResult != null) ourResult.setScopeMatch(true);
        handler.setNestedResult(ourResult);
        myMatchingVisitor.setResult(handler.handle(methodNameNode2, myMatchingVisitor.getMatchContext()));

        if (handler.getNestedResult() != null) { // some constraint prevent from adding
          handler.setNestedResult(null);
          copyResults(ourResult);
        }
      }
      else if (ourResult != null) {
        copyResults(ourResult);
      }
    }
  }

  private void matchImplicitQualifier(MatchingHandler matchingHandler, PsiElement target, MatchContext context) {
    if (!(matchingHandler instanceof SubstitutionHandler)) {
      myMatchingVisitor.setResult(false);
      return;
    }
    final SubstitutionHandler substitutionHandler = (SubstitutionHandler)matchingHandler;
    final MatchPredicate predicate = substitutionHandler.getPredicate();
    if (substitutionHandler.getMinOccurs() != 0) {
      myMatchingVisitor.setResult(false);
      return;
    }
    if (predicate == null) {
      myMatchingVisitor.setResult(true);
      return;
    }
    if (target == null) {
      myMatchingVisitor.setResult(false);
      return;
    }
    if (target instanceof PsiModifierListOwner && ((PsiModifierListOwner)target).hasModifierProperty(PsiModifier.STATIC)) {
      myMatchingVisitor.setResult(predicate.match(PsiTreeUtil.getParentOfType(target, PsiClass.class), context));
    } else {
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(target.getProject());
      final PsiExpression implicitReference = factory.createExpressionFromText("this", target);
      myMatchingVisitor.setResult(predicate.match(implicitReference, context));
    }
  }

  @Override
  public void visitMethodCallExpression(final PsiMethodCallExpression mcall) {
    final PsiElement element = myMatchingVisitor.getElement();
    if (!(element instanceof PsiMethodCallExpression)) {
      myMatchingVisitor.setResult(false);
      return;
    }
    final PsiMethodCallExpression mcall2 = (PsiMethodCallExpression)element;
    final PsiReferenceExpression mcallRef1 = mcall.getMethodExpression();
    final PsiReferenceExpression mcallRef2 = mcall2.getMethodExpression();

    final PsiElement patternMethodName = mcallRef1.getReferenceNameElement();
    final boolean isTypedVar = myMatchingVisitor.getMatchContext().getPattern().isTypedVar(patternMethodName);

    if (!isTypedVar && !myMatchingVisitor.matchText(patternMethodName, mcallRef2.getReferenceNameElement())) {
      myMatchingVisitor.setResult(false);
      return;
    }

    final PsiExpression patternQualifier = mcallRef1.getQualifierExpression();
    final PsiExpression matchedQualifier = mcallRef2.getQualifierExpression();
    if (patternQualifier != null) {

      if (matchedQualifier != null) {
        myMatchingVisitor.setResult(myMatchingVisitor.match(patternQualifier, matchedQualifier));
        if (!myMatchingVisitor.getResult()) return;
      }
      else {
        final PsiMethod method = mcall2.resolveMethod();
        if (method != null) {
          if (patternQualifier instanceof PsiThisExpression) {
            myMatchingVisitor.setResult(!method.hasModifierProperty(PsiModifier.STATIC));
            return;
          }
        }
        final MatchingHandler handler = myMatchingVisitor.getMatchContext().getPattern().getHandler(patternQualifier);
        matchImplicitQualifier(handler, method, myMatchingVisitor.getMatchContext());
        if (!myMatchingVisitor.getResult()) {
          return;
        }
      }
    }

    myMatchingVisitor.setResult(myMatchingVisitor.matchSons(mcall.getArgumentList(), mcall2.getArgumentList()));

    if (myMatchingVisitor.getResult()) {
      myMatchingVisitor.setResult(matchTypeParameters(mcallRef1, mcallRef2));
    }

    if (myMatchingVisitor.getResult() && isTypedVar) {
      boolean res = myMatchingVisitor.getResult();
      res &= myMatchingVisitor.handleTypedElement(patternMethodName, mcallRef2.getReferenceNameElement());
      myMatchingVisitor.setResult(res);
    }
  }

  private boolean matchTypeParameters(PsiJavaCodeReferenceElement mcallRef1, PsiJavaCodeReferenceElement mcallRef2) {
    final PsiReferenceParameterList patternParameterList = mcallRef1.getParameterList();
    if (patternParameterList == null) {
      return true;
    }
    final PsiTypeElement[] patternTypeElements = patternParameterList.getTypeParameterElements();
    if (patternTypeElements.length == 0) {
      return true;
    }
    PsiReferenceParameterList matchedParameterList = mcallRef2.getParameterList();
    if (matchedParameterList == null) {
      return false;
    }
    if (matchedParameterList.getFirstChild() == null) { // check inferred type parameters
      final JavaResolveResult resolveResult = mcallRef2.advancedResolve(false);
      final PsiMethod targetMethod = (PsiMethod)resolveResult.getElement();
      if (targetMethod == null) {
        return false;
      }
      final PsiTypeParameterList typeParameterList = targetMethod.getTypeParameterList();
      if (typeParameterList == null) {
        return false;
      }
      final PsiTypeParameter[] typeParameters = typeParameterList.getTypeParameters();
      final PsiSubstitutor substitutor = resolveResult.getSubstitutor();
      matchedParameterList = (PsiReferenceParameterList)matchedParameterList.copy();
      for (final PsiTypeParameter typeParameter : typeParameters) {
        final PsiType type = substitutor.substitute(typeParameter);
        if (type == null) {
          return false;
        }
        final PsiTypeElement matchedTypeElement = JavaPsiFacade.getElementFactory(mcallRef1.getProject()).createTypeElement(type);
        matchedParameterList.add(matchedTypeElement);
      }
    }
    final PsiTypeElement[] matchedTypeElements = matchedParameterList.getTypeParameterElements();
    return myMatchingVisitor.matchSequentially(patternTypeElements, matchedTypeElements);
  }

  @Override
  public void visitExpressionStatement(final PsiExpressionStatement expr) {
    final PsiElement other = myMatchingVisitor.getElement();
    if (other instanceof PsiExpressionStatement) {
      final PsiExpressionStatement expr2 = (PsiExpressionStatement)other;
      myMatchingVisitor.setResult(myMatchingVisitor.match(expr.getExpression(), expr2.getExpression()));
    }
    else {
      myMatchingVisitor.setResult(false);
    }
  }

  @Override
  public void visitLiteralExpression(final PsiLiteralExpression const1) {
    final PsiLiteralExpression const2 = (PsiLiteralExpression)myMatchingVisitor.getElement();

    final MatchingHandler handler = (MatchingHandler)const1.getUserData(CompiledPattern.HANDLER_KEY);
    if (handler instanceof SubstitutionHandler) {
      final PsiType type1 = const1.getType();
      if (type1 != null && !type1.equals(const2.getType())) {
        myMatchingVisitor.setResult(false);
      }
      else {
        int offset = 0;
        int length = const2.getTextLength();
        final String text = const2.getText();

        if (StringUtil.isQuotedString(text)) {
          length--;
          offset++;
        }
        myMatchingVisitor.setResult(((SubstitutionHandler)handler).handle(const2, offset, length, myMatchingVisitor.getMatchContext()));
      }
    }
    else if (handler != null) {
      myMatchingVisitor.setResult(handler.match(const1, const2, myMatchingVisitor.getMatchContext()));
    }
    else {
      final Object value1 = const1.getValue();
      final Object value2 = const2.getValue();
      if ((value1 instanceof String || value1 instanceof Character) && (value2 instanceof String || value2 instanceof Character)) {
        myMatchingVisitor.setResult(myMatchingVisitor.matchText(value1.toString(), value2.toString()));
      }
      else if (value1 != null && value2 != null) {
        myMatchingVisitor.setResult(value1.equals(value2));
      }
      else {
        // matches null literals
        myMatchingVisitor.setResult(myMatchingVisitor.matchText(const1, const2));
      }
    }
  }

  @Override
  public void visitAssignmentExpression(final PsiAssignmentExpression assign) {
    final PsiElement other = myMatchingVisitor.getElement();
    if (other instanceof PsiAssignmentExpression) {
      final PsiAssignmentExpression assign2 = (PsiAssignmentExpression)other;

      myMatchingVisitor.setResult(assign.getOperationTokenType().equals(assign2.getOperationTokenType()) &&
                                  myMatchingVisitor.match(assign.getLExpression(), assign2.getLExpression()) &&
                                  myMatchingVisitor.match(assign.getRExpression(), assign2.getRExpression()));
    }
    else {
      myMatchingVisitor.setResult(false);
    }
  }

  @Override
  public void visitIfStatement(final PsiIfStatement if1) {
    final PsiIfStatement if2 = (PsiIfStatement)myMatchingVisitor.getElement();

    final PsiStatement elseBranch = if1.getElseBranch();
    myMatchingVisitor.setResult(myMatchingVisitor.match(if1.getCondition(), if2.getCondition()) &&
                                matchBody(if1.getThenBranch(), if2.getThenBranch()) &&
                                (elseBranch == null || matchBody(elseBranch, if2.getElseBranch())));
  }

  @Override
  public void visitSwitchStatement(final PsiSwitchStatement switch1) {
    final PsiSwitchStatement switch2 = (PsiSwitchStatement)myMatchingVisitor.getElement();

    myMatchingVisitor.setResult(myMatchingVisitor.match(switch1.getExpression(), switch2.getExpression()) &&
                                myMatchingVisitor.matchSons(switch1.getBody(), switch2.getBody()));
  }

  @Override
  public void visitForStatement(final PsiForStatement for1) {
    final PsiForStatement for2 = (PsiForStatement)myMatchingVisitor.getElement();

    myMatchingVisitor.setResult(myMatchingVisitor.match(for1.getInitialization(), for2.getInitialization()) &&
                                myMatchingVisitor.match(for1.getCondition(), for2.getCondition()) &&
                                myMatchingVisitor.match(for1.getUpdate(), for2.getUpdate()) &&
                                matchBody(for1.getBody(), for2.getBody()));
  }

  @Override
  public void visitForeachStatement(PsiForeachStatement for1) {
    final PsiForeachStatement for2 = (PsiForeachStatement)myMatchingVisitor.getElement();

    myMatchingVisitor.setResult(myMatchingVisitor.match(for1.getIterationParameter(), for2.getIterationParameter()) &&
                                myMatchingVisitor.match(for1.getIteratedValue(), for2.getIteratedValue()) &&
                                matchBody(for1.getBody(), for2.getBody()));
  }

  @Override
  public void visitWhileStatement(final PsiWhileStatement while1) {
    final PsiWhileStatement while2 = (PsiWhileStatement)myMatchingVisitor.getElement();

    myMatchingVisitor.setResult(myMatchingVisitor.match(while1.getCondition(), while2.getCondition()) &&
                                matchBody(while1.getBody(), while2.getBody()));
  }

  @Override
  public void visitBlockStatement(final PsiBlockStatement block) {
    final PsiElement other = myMatchingVisitor.getElement();
    if (other instanceof PsiCodeBlock) {
      myMatchingVisitor.setResult(!(other.getParent() instanceof PsiBlockStatement) &&
                                  myMatchingVisitor.matchSons(block.getCodeBlock(), other));
    }
    else {
      final PsiBlockStatement block2 = (PsiBlockStatement)other;
      myMatchingVisitor.setResult(myMatchingVisitor.matchSons(block, block2));
    }
  }

  @Override
  public void visitDeclarationStatement(final PsiDeclarationStatement dcl) {
    final PsiDeclarationStatement declaration = (PsiDeclarationStatement)myMatchingVisitor.getElement();
    myMatchingVisitor.setResult(myMatchingVisitor.matchInAnyOrder(dcl.getDeclaredElements(), declaration.getDeclaredElements()));
  }

  @Override
  public void visitDoWhileStatement(final PsiDoWhileStatement while1) {
    final PsiDoWhileStatement while2 = (PsiDoWhileStatement)myMatchingVisitor.getElement();

    myMatchingVisitor.setResult(myMatchingVisitor.match(while1.getCondition(), while2.getCondition()) &&
                                matchBody(while1.getBody(), while2.getBody()));
  }

  @Override
  public void visitReturnStatement(final PsiReturnStatement return1) {
    final PsiReturnStatement return2 = (PsiReturnStatement)myMatchingVisitor.getElement();

    myMatchingVisitor.setResult(myMatchingVisitor.match(return1.getReturnValue(), return2.getReturnValue()));
  }

  @Override
  public void visitPostfixExpression(final PsiPostfixExpression postfix) {
    final PsiPostfixExpression postfix2 = (PsiPostfixExpression)myMatchingVisitor.getElement();

    myMatchingVisitor.setResult(postfix.getOperationTokenType().equals(postfix2.getOperationTokenType())
                                && myMatchingVisitor.match(postfix.getOperand(), postfix2.getOperand()));
  }

  @Override
  public void visitPrefixExpression(final PsiPrefixExpression prefix) {
    final PsiPrefixExpression prefix2 = (PsiPrefixExpression)myMatchingVisitor.getElement();

    myMatchingVisitor.setResult(prefix.getOperationTokenType().equals(prefix2.getOperationTokenType())
                                && myMatchingVisitor.match(prefix.getOperand(), prefix2.getOperand()));
  }

  @Override
  public void visitAssertStatement(final PsiAssertStatement assert1) {
    final PsiAssertStatement assert2 = (PsiAssertStatement)myMatchingVisitor.getElement();

    myMatchingVisitor.setResult(myMatchingVisitor.match(assert1.getAssertCondition(), assert2.getAssertCondition()) &&
                                myMatchingVisitor.match(assert1.getAssertDescription(), assert2.getAssertDescription()));
  }

  @Override
  public void visitBreakStatement(final PsiBreakStatement break1) {
    final PsiBreakStatement break2 = (PsiBreakStatement)myMatchingVisitor.getElement();

    myMatchingVisitor.setResult(myMatchingVisitor.match(break1.getLabelIdentifier(), break2.getLabelIdentifier()));
  }

  @Override
  public void visitContinueStatement(final PsiContinueStatement continue1) {
    final PsiContinueStatement continue2 = (PsiContinueStatement)myMatchingVisitor.getElement();

    myMatchingVisitor.setResult(myMatchingVisitor.match(continue1.getLabelIdentifier(), continue2.getLabelIdentifier()));
  }

  @Override
  public void visitSuperExpression(final PsiSuperExpression super1) {
    myMatchingVisitor.setResult(myMatchingVisitor.getElement() instanceof PsiSuperExpression);
  }

  @Override
  public void visitThisExpression(final PsiThisExpression this1) {
    myMatchingVisitor.setResult(myMatchingVisitor.getElement() instanceof PsiThisExpression);
  }

  @Override
  public void visitSynchronizedStatement(final PsiSynchronizedStatement synchronized1) {
    final PsiSynchronizedStatement synchronized2 = (PsiSynchronizedStatement)myMatchingVisitor.getElement();

    myMatchingVisitor.setResult(myMatchingVisitor.match(synchronized1.getLockExpression(), synchronized2.getLockExpression()) &&
                                myMatchingVisitor.matchSons(synchronized1.getBody(), synchronized2.getBody()));
  }

  @Override
  public void visitThrowStatement(final PsiThrowStatement throw1) {
    final PsiThrowStatement throw2 = (PsiThrowStatement)myMatchingVisitor.getElement();

    myMatchingVisitor.setResult(myMatchingVisitor.match(throw1.getException(), throw2.getException()));
  }

  @Override
  public void visitParenthesizedExpression(PsiParenthesizedExpression expr) {
    if (myMatchingVisitor.getElement() instanceof PsiParenthesizedExpression) {
      myMatchingVisitor.setResult(myMatchingVisitor.matchSons(expr, myMatchingVisitor.getElement()));
    }
    else {
      myMatchingVisitor.setResult(false);
    }
  }

  @Override
  public void visitCatchSection(PsiCatchSection section) {
    final PsiCatchSection section2 = (PsiCatchSection)myMatchingVisitor.getElement();
    final MatchingHandler handler = myMatchingVisitor.getMatchContext().getPattern().getHandler(section);
    myMatchingVisitor.setResult(myMatchingVisitor.match(section.getParameter(), section2.getParameter()) &&
                                myMatchingVisitor.matchSons(section.getCatchBlock(), section2.getCatchBlock()) &&
                                ((SubstitutionHandler)handler).handle(section2, myMatchingVisitor.getMatchContext()));
  }

  @Override
  public void visitTryStatement(final PsiTryStatement try1) {
    final PsiTryStatement try2 = (PsiTryStatement)myMatchingVisitor.getElement();

    myMatchingVisitor.setResult(myMatchingVisitor.matchSons(try1.getTryBlock(), try2.getTryBlock()));
    if (!myMatchingVisitor.getResult()) return;

    final PsiResourceList resourceList1 = try1.getResourceList();
    final PsiCatchSection[] catches1 = try1.getCatchSections();
    final PsiCodeBlock finally1 = try1.getFinallyBlock();

    final PsiResourceList resourceList2 = try2.getResourceList();
    final PsiCatchSection[] catches2 = try2.getCatchSections();
    final PsiCodeBlock finally2 = try2.getFinallyBlock();

    if (!myMatchingVisitor.getMatchContext().getOptions().isLooseMatching() &&
        ((catches1.length == 0 && catches2.length != 0) ||
         (finally1 == null && finally2 != null) ||
         (resourceList1 == null && resourceList2 != null)) ||
        catches2.length < catches1.length
      ) {
      myMatchingVisitor.setResult(false);
    }
    else {
      final List<PsiElement> unmatchedElements = new SmartList<>();

      if (resourceList1 != null) {
        if (resourceList2 == null) {
          myMatchingVisitor.setResult(false);
          return;
        }
        final List<PsiResourceListElement> resources1 = PsiTreeUtil.getChildrenOfTypeAsList(resourceList1, PsiResourceListElement.class);
        final List<PsiResourceListElement> resources2 = PsiTreeUtil.getChildrenOfTypeAsList(resourceList2, PsiResourceListElement.class);
        myMatchingVisitor.setResult(myMatchingVisitor.matchInAnyOrder(
          resources1.toArray(new PsiResourceListElement[resources1.size()]),
          resources2.toArray(new PsiResourceListElement[resources2.size()])));
        if (!myMatchingVisitor.getResult()) return;
      }
      else if (resourceList2 != null){
        unmatchedElements.add(resourceList2);
      }

      ContainerUtil.addAll(unmatchedElements, catches2);
      myMatchingVisitor.getMatchContext().setMatchedElementsListener(matchedElements -> unmatchedElements.removeAll(matchedElements));
      myMatchingVisitor.setResult(myMatchingVisitor.matchInAnyOrder(catches1, catches2));

      if (finally1 != null) {
        myMatchingVisitor.setResult(myMatchingVisitor.matchSons(finally1, finally2));
      } else if (finally2 != null) {
        unmatchedElements.add(finally2);
      }

      if (myMatchingVisitor.getResult() && !unmatchedElements.isEmpty()) {
        try2.putUserData(GlobalMatchingVisitor.UNMATCHED_ELEMENTS_KEY, unmatchedElements);
      }
    }
  }

  @Override
  public void visitSwitchLabelStatement(final PsiSwitchLabelStatement case1) {
    final PsiSwitchLabelStatement case2 = (PsiSwitchLabelStatement)myMatchingVisitor.getElement();

    myMatchingVisitor.setResult(case1.isDefaultCase() == case2.isDefaultCase() &&
                                myMatchingVisitor.match(case1.getCaseValue(), case2.getCaseValue()));
  }

  @Override
  public void visitInstanceOfExpression(final PsiInstanceOfExpression instanceOf) {
    final PsiElement other = myMatchingVisitor.getElement();
    if (other instanceof PsiInstanceOfExpression) {
      final PsiInstanceOfExpression instanceOf2 = (PsiInstanceOfExpression)other;
      myMatchingVisitor.setResult(myMatchingVisitor.match(instanceOf.getOperand(), instanceOf2.getOperand()));
      if (myMatchingVisitor.getResult()) {
        final PsiTypeElement checkType = instanceOf.getCheckType();
        if (checkType != null) {
          myMatchingVisitor.setResult(myMatchingVisitor.match(checkType, instanceOf2.getCheckType()));
        }
      }
    }
    else {
      myMatchingVisitor.setResult(false);
    }
  }

  @Override
  public void visitNewExpression(final PsiNewExpression new1) {
    final PsiElement other = myMatchingVisitor.getElement();
    final PsiJavaCodeReferenceElement classReference = new1.getClassReference();
    if (other instanceof PsiArrayInitializerExpression &&
        other.getParent() instanceof PsiVariable &&
        new1.getArrayDimensions().length == 0 &&
        new1.getArrayInitializer() != null
      ) {
      final MatchContext matchContext = myMatchingVisitor.getMatchContext();
      final MatchingHandler handler = matchContext.getPattern().getHandler(classReference);
      final boolean looseMatching = myMatchingVisitor.getMatchContext().getOptions().isLooseMatching();
      if ((handler instanceof SubstitutionHandler && ((SubstitutionHandler)handler).getMinOccurs() != 0) || !looseMatching) {
        myMatchingVisitor.setResult(false);
        return;
      }
      final PsiType otherType = ((PsiArrayInitializerExpression)other).getType();
      if (handler instanceof SubstitutionHandler && otherType != null) {
        final PsiElementFactory factory = JavaPsiFacade.getElementFactory(other.getProject());
        final PsiTypeElement otherTypeElement = factory.createTypeElement(otherType.getDeepComponentType());
        final SubstitutionHandler substitutionHandler = (SubstitutionHandler)handler;
        final MatchPredicate predicate = substitutionHandler.getPredicate();
        myMatchingVisitor.setResult(predicate == null || predicate.match(otherTypeElement, matchContext));
      }
      else {
        final PsiType type = new1.getType();
        myMatchingVisitor.setResult(type != null && type.equals(otherType));
      }
      if (myMatchingVisitor.getResult()) {
        myMatchingVisitor.matchSons(new1.getArrayInitializer(), other);
      }
      return;
    }

    if (!(other instanceof PsiNewExpression)) {
      myMatchingVisitor.setResult(false);
      return;
    }
    final PsiNewExpression new2 = (PsiNewExpression)other;

    if (classReference != null) {
      if (new2.getClassReference() != null) {
        myMatchingVisitor.setResult(myMatchingVisitor.match(classReference, new2.getClassReference()) &&
                                    myMatchingVisitor.matchSons(new1.getArrayInitializer(), new2.getArrayInitializer()));

        if (myMatchingVisitor.getResult()) {
          // matching dims
          matchArrayDims(new1, new2);
        }
        return;
      }
      else {
        // match array of primitive by new 'T();
        final PsiKeyword newKeyword = PsiTreeUtil.getChildOfType(new2, PsiKeyword.class);
        final PsiElement element = PsiTreeUtil.getNextSiblingOfType(newKeyword, PsiWhiteSpace.class);

        if (element != null && element.getNextSibling() instanceof PsiKeyword) {
          myMatchingVisitor.setResult(myMatchingVisitor.match(classReference, element.getNextSibling()) &&
                                      myMatchingVisitor.matchSons(new1.getArrayInitializer(), new2.getArrayInitializer()));
          if (myMatchingVisitor.getResult()) {
            // matching dims
            matchArrayDims(new1, new2);
          }

          return;
        }
      }
    }

    if (classReference == new2.getClassReference()) {
      // probably anonymous class or array of primitive type
      myMatchingVisitor.setResult(myMatchingVisitor.matchSons(new1, new2));
    }
    else if (new1.getAnonymousClass() == null &&
             classReference != null &&
             new2.getAnonymousClass() != null) {
      // allow matching anonymous class without pattern
      myMatchingVisitor.setResult(myMatchingVisitor.match(classReference, new2.getAnonymousClass().getBaseClassReference()) &&
                                  myMatchingVisitor.matchSons(new1.getArgumentList(), new2.getArgumentList()));
    }
    else {
      myMatchingVisitor.setResult(false);
    }
  }

  @Override
  public void visitKeyword(PsiKeyword keyword) {
    myMatchingVisitor.setResult(myMatchingVisitor.matchText(keyword, myMatchingVisitor.getElement()));
  }

  @Override
  public void visitTypeCastExpression(final PsiTypeCastExpression cast) {
    final PsiElement other = myMatchingVisitor.getElement();
    if (other instanceof PsiTypeCastExpression) {
      final PsiTypeCastExpression cast2 = (PsiTypeCastExpression)other;
      myMatchingVisitor.setResult(myMatchingVisitor.match(cast.getCastType(), cast2.getCastType()) &&
                                  myMatchingVisitor.match(cast.getOperand(), cast2.getOperand()));
    }
    else {
      myMatchingVisitor.setResult(false);
    }
  }

  @Override
  public void visitClassObjectAccessExpression(final PsiClassObjectAccessExpression expr) {
    final PsiElement other = myMatchingVisitor.getElement();
    if (other instanceof PsiClassObjectAccessExpression) {
      final PsiClassObjectAccessExpression expr2 = (PsiClassObjectAccessExpression)other;
      myMatchingVisitor.setResult(myMatchingVisitor.match(expr.getOperand(), expr2.getOperand()));
    }
    else {
      myMatchingVisitor.setResult(false);
    }
  }

  @Override
  public void visitReferenceElement(final PsiJavaCodeReferenceElement ref) {
    final PsiElement other = myMatchingVisitor.getElement();
    final PsiAnnotation[] annotations = PsiTreeUtil.getChildrenOfType(ref, PsiAnnotation.class);
    if (annotations != null) {
      final PsiAnnotation[] otherAnnotations = PsiTreeUtil.getChildrenOfType(other, PsiAnnotation.class);
      myMatchingVisitor.setResult(otherAnnotations != null && myMatchingVisitor.matchInAnyOrder(annotations, otherAnnotations));
      if (!myMatchingVisitor.getResult()) return;
    }
    myMatchingVisitor.setResult(matchType(ref, other));
  }

  @Override
  public void visitTypeElement(final PsiTypeElement typeElement) {
    final PsiElement other = myMatchingVisitor.getElement(); // might not be a PsiTypeElement

    final PsiAnnotation[] annotations = PsiTreeUtil.getChildrenOfType(typeElement, PsiAnnotation.class);
    // also can't use AnnotationOwner api because it is not implemented completely yet (see e.g. ClsTypeParameterImpl)
    final PsiAnnotation[] annotations2 = PsiTreeUtil.getChildrenOfType(other, PsiAnnotation.class);
    if (annotations != null) {
      myMatchingVisitor.setResult(annotations2 != null && myMatchingVisitor.matchInAnyOrder(annotations, annotations2));
      if (!myMatchingVisitor.getResult()) return;
    }
    final PsiTypeElement[] typeElementChildren = PsiTreeUtil.getChildrenOfType(typeElement, PsiTypeElement.class);
    if (typeElementChildren != null && typeElementChildren.length > 1) {
      // multi catch type element
      final PsiTypeElement[] typeElementChildren2 = PsiTreeUtil.getChildrenOfType(other, PsiTypeElement.class);
      myMatchingVisitor.setResult(
        typeElementChildren2 != null && myMatchingVisitor.matchInAnyOrder(typeElementChildren, typeElementChildren2));
    }
    else {
      myMatchingVisitor.setResult(matchType(typeElement, other));
    }
  }

  @Override
  public void visitTypeParameter(PsiTypeParameter psiTypeParameter) {
    final PsiTypeParameter parameter = (PsiTypeParameter)myMatchingVisitor.getElement();
    final PsiIdentifier identifier = psiTypeParameter.getNameIdentifier();
    final PsiIdentifier identifier2 = parameter.getNameIdentifier();

    final MatchingHandler handler = myMatchingVisitor.getMatchContext().getPattern().getHandler(identifier);
    if (handler instanceof SubstitutionHandler) {
      myMatchingVisitor.setResult(((SubstitutionHandler)handler).handle(identifier2, myMatchingVisitor.getMatchContext()));
    }
    else {
      myMatchingVisitor.setResult(myMatchingVisitor.matchText(identifier, identifier2));
    }

    if (myMatchingVisitor.getResult()) {
      myMatchingVisitor.setResult(matchInAnyOrder(psiTypeParameter.getExtendsList(), parameter.getExtendsList()));
    }
    if (myMatchingVisitor.getResult()) {
      myMatchingVisitor.setResult(myMatchingVisitor.matchInAnyOrder(psiTypeParameter.getAnnotations(), parameter.getAnnotations()));
    }
  }

  @Override
  public void visitClass(PsiClass clazz) {
    final PsiClass clazz2 = (PsiClass)myMatchingVisitor.getElement();
    if (clazz.hasTypeParameters()) {
      myMatchingVisitor.setResult(myMatchingVisitor.match(clazz.getTypeParameterList(), clazz2.getTypeParameterList()));
      if (!myMatchingVisitor.getResult()) return;
    }

    final PsiDocComment comment = clazz.getDocComment();
    if (comment != null) {
      myMatchingVisitor.setResult(myMatchingVisitor.match(comment, clazz2));
      if (!myMatchingVisitor.getResult()) return;
    }

    final PsiIdentifier identifier = clazz.getNameIdentifier();
    final boolean isTypedVar = myMatchingVisitor.getMatchContext().getPattern().isTypedVar(identifier);

    if (clazz.getModifierList().getTextLength() > 0) {
      if (!myMatchingVisitor.match(clazz.getModifierList(), clazz2.getModifierList())) {
        myMatchingVisitor.setResult(false);
        return;
      }
    }

    myMatchingVisitor.setResult((isTypedVar || myMatchingVisitor.matchText(identifier, clazz2.getNameIdentifier())) &&
                                compareClasses(clazz, clazz2));

    if (myMatchingVisitor.getResult() && isTypedVar) {
      PsiElement id = clazz2.getNameIdentifier();
      if (id == null) id = clazz2;
      final SubstitutionHandler handler = (SubstitutionHandler)myMatchingVisitor.getMatchContext().getPattern().getHandler(identifier);
      if (handler.isSubtype() || handler.isStrictSubtype()) {
        myMatchingVisitor.setResult(checkMatchWithinHierarchy(id, handler, identifier));
      }
      else {
        myMatchingVisitor.setResult(myMatchingVisitor.handleTypedElement(identifier, id));
      }
    }
  }

  @Override
  public void visitTypeParameterList(PsiTypeParameterList psiTypeParameterList) {
    myMatchingVisitor.setResult(myMatchingVisitor.matchSequentially(
      psiTypeParameterList.getFirstChild(),
      myMatchingVisitor.getElement().getFirstChild()
    ));
  }

  @Override
  public void visitMethod(PsiMethod method) {
    final PsiIdentifier methodNameNode = method.getNameIdentifier();
    final boolean isTypedVar = myMatchingVisitor.getMatchContext().getPattern().isTypedVar(methodNameNode);
    final PsiMethod method2 = (PsiMethod)myMatchingVisitor.getElement();

    myMatchingVisitor.getMatchContext().pushResult();

    try {
      final PsiDocComment docComment = method.getDocComment();
      if (docComment != null) {
        myMatchingVisitor.setResult(myMatchingVisitor.match(docComment, method2));
        if (!myMatchingVisitor.getResult()) return;
      }
      if (method.hasTypeParameters()) {
        myMatchingVisitor.setResult(
          myMatchingVisitor.match(method.getTypeParameterList(), ((PsiMethod)myMatchingVisitor.getElement()).getTypeParameterList()));

        if (!myMatchingVisitor.getResult()) return;
      }

      if (!checkHierarchy(method2, method)) {
        myMatchingVisitor.setResult(false);
        return;
      }

      myMatchingVisitor.setResult(method.isConstructor() == method2.isConstructor() &&
                                  (myMatchingVisitor.matchText(method.getNameIdentifier(), method2.getNameIdentifier()) || isTypedVar) &&
                                  myMatchingVisitor.match(method.getModifierList(), method2.getModifierList()) &&
                                  myMatchingVisitor.matchSons(method.getParameterList(), method2.getParameterList()) &&
                                  myMatchingVisitor.match(method.getReturnTypeElement(), method2.getReturnTypeElement()) &&
                                                    matchInAnyOrder(method.getThrowsList(), method2.getThrowsList()) &&
                                  myMatchingVisitor.matchSonsOptionally(method.getBody(), method2.getBody()));
    }
    finally {
      saveOrDropResult(methodNameNode, isTypedVar, method2.getNameIdentifier());
    }
  }
}

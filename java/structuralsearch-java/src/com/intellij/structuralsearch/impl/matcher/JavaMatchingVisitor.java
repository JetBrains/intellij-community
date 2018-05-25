// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.structuralsearch.impl.matcher.handlers.MatchingHandler;
import com.intellij.structuralsearch.impl.matcher.handlers.SubstitutionHandler;
import com.intellij.structuralsearch.impl.matcher.iterators.DocValuesIterator;
import com.intellij.structuralsearch.impl.matcher.iterators.HierarchyNodeIterator;
import com.intellij.structuralsearch.impl.matcher.predicates.MatchPredicate;
import com.intellij.structuralsearch.impl.matcher.predicates.NotPredicate;
import com.intellij.structuralsearch.impl.matcher.predicates.RegExpPredicate;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
        comment2 = ObjectUtils.tryCast(myMatchingVisitor.getElement().getFirstChild(), PsiComment.class);
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
      if (!myMatchingVisitor.setResult(!list.hasModifierProperty(modifier) || list2.hasModifierProperty(modifier))) {
        return;
      }
    }

    final PsiAnnotation[] annotations = list.getAnnotations();
    if (annotations.length > 0) {
      final HashSet<PsiAnnotation> annotationSet = new HashSet<>(Arrays.asList(annotations));

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

          annotationSet.remove(annotation);
        }
      }

      if (!annotationSet.isEmpty()) {
        final PsiAnnotation[] otherAnnotations = list2.getAnnotations();
        final List<PsiElement> unmatchedElements = new SmartList<>(otherAnnotations);
        myMatchingVisitor.getMatchContext().pushMatchedElementsListener(elements -> unmatchedElements.removeAll(elements));
        try {
          myMatchingVisitor.setResult(myMatchingVisitor.matchInAnyOrder(annotationSet.toArray(PsiAnnotation.EMPTY_ARRAY), otherAnnotations));
          list2.putUserData(GlobalMatchingVisitor.UNMATCHED_ELEMENTS_KEY, unmatchedElements);
        }
        finally {
          myMatchingVisitor.getMatchContext().popMatchedElementsListener();
        }
      }
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

    if (!myMatchingVisitor.setResult(isTypedVar || tag.getName().equals(tag2.getName()))) return;

    PsiElement psiDocTagValue = tag.getValueElement();
    boolean isTypedValue = false;

    if (psiDocTagValue != null) {
      final PsiElement[] children = psiDocTagValue.getChildren();
      if (children.length == 1) {
        psiDocTagValue = children[0];
      }
      isTypedValue = myMatchingVisitor.getMatchContext().getPattern().isTypedVar(psiDocTagValue);

      if (isTypedValue) {
        if (tag2.getValueElement() != null) {
          if (!myMatchingVisitor.setResult(myMatchingVisitor.handleTypedElement(psiDocTagValue, tag2.getValueElement()))) return;
        }
        else {
          if (!myMatchingVisitor.setResult(myMatchingVisitor.allowsAbsenceOfMatch(psiDocTagValue))) return;
        }
      }
    }

    if (!isTypedValue && !myMatchingVisitor.setResult(myMatchingVisitor.matchInAnyOrder(new DocValuesIterator(tag.getFirstChild()),
                                                                                        new DocValuesIterator(tag2.getFirstChild())))) {
      return;
    }

    if (isTypedVar) {
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
    final PsiArrayInitializerExpression other = getExpression(PsiArrayInitializerExpression.class);
    if (other == null) return;
    myMatchingVisitor.setResult(myMatchingVisitor.matchSequentially(expression.getInitializers(), other.getInitializers()));
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
    final PsiElement element = myMatchingVisitor.getElement();

    myMatchingVisitor.setResult((!(element instanceof PsiJavaToken) || token.getTokenType() == ((PsiJavaToken)element).getTokenType())
                                && (myMatchingVisitor.getMatchContext().getPattern().isTypedVar(token)
                                    ? myMatchingVisitor.handleTypedElement(token, element)
                                    : myMatchingVisitor.matchText(token, element)));
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
    final PsiNameValuePair other = (PsiNameValuePair)myMatchingVisitor.getElement();

    final MatchContext context = myMatchingVisitor.getMatchContext();
    final PsiIdentifier nameIdentifier = pair.getNameIdentifier();
    final boolean isTypedVar = context.getPattern().isTypedVar(nameIdentifier);
    if (nameIdentifier != null) context.pushResult();
    final PsiIdentifier otherIdentifier = other.getNameIdentifier();
    try {
      final PsiAnnotationMemberValue value = pair.getValue();
      if (myMatchingVisitor.setResult(myMatchingVisitor.match(value, other.getValue()))) {
        if (nameIdentifier != null) {
          myMatchingVisitor.setResult(isTypedVar ||
            myMatchingVisitor.matchText(nameIdentifier.getText(),
                                        otherIdentifier == null ? PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME : otherIdentifier.getText()));
        }
        else {
          myMatchingVisitor.setResult(otherIdentifier == null ||
                                      PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME.equals(otherIdentifier.getText()));
        }
      }
    } finally {
      final PsiIdentifier matchNode;
      if (otherIdentifier != null) {
        matchNode = otherIdentifier;
      }
      else {
        final PsiElementFactory factory = JavaPsiFacade.getElementFactory(other.getProject());
        final PsiAnnotation annotation =
          (PsiAnnotation)factory.createStatementFromText("@Anno(value=\"\")", other).getFirstChild().getFirstChild();
        matchNode = annotation.getParameterList().getAttributes()[0].getNameIdentifier();
      }
      if (nameIdentifier != null) myMatchingVisitor.scopeMatch(nameIdentifier, isTypedVar, matchNode);
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
    if (comment != null && !myMatchingVisitor.setResult(myMatchingVisitor.match(comment, other))) return;
    if (!myMatchingVisitor.setResult(checkHierarchy(other, psiField))) return;
    super.visitField(psiField);
  }

  @Override
  public void visitAnonymousClass(final PsiAnonymousClass clazz) {
    final PsiAnonymousClass clazz2 = (PsiAnonymousClass)myMatchingVisitor.getElement();
    final PsiElement classReference = clazz.getBaseClassReference();
    final boolean isTypedVar = myMatchingVisitor.getMatchContext().getPattern().isTypedVar(classReference);

    if (myMatchingVisitor.setResult((isTypedVar || myMatchingVisitor.match(clazz.getBaseClassReference(), clazz2.getBaseClassReference())) &&
                                    myMatchingVisitor.matchSons(clazz.getArgumentList(), clazz2.getArgumentList()) &&
                                    compareClasses(clazz, clazz2)) && isTypedVar) {
      myMatchingVisitor.setResult(matchType(classReference, clazz2.getBaseClassReference()));
    }
  }

  @Override
  public void visitLambdaExpression(PsiLambdaExpression expression) {
    final PsiLambdaExpression other = getExpression(PsiLambdaExpression.class);
    if (other == null) return;
    final PsiParameterList parameterList1 = expression.getParameterList();
    if (!myMatchingVisitor.setResult(
      parameterList1.isEmpty() || myMatchingVisitor.matchSons(parameterList1, other.getParameterList()))) return;
    final PsiElement body1 = getElementToMatch(expression.getBody());
    myMatchingVisitor.setResult(body1 == null || myMatchingVisitor.matchSequentially(body1, getElementToMatch(other.getBody())));
  }

  private static PsiElement getElementToMatch(PsiElement element) {
    if (element instanceof PsiCodeBlock) {
      final List<PsiElement> list = PsiTreeUtil.getChildrenOfAnyType(element, PsiStatement.class, PsiComment.class);
      if (list.isEmpty()) return null;
      element = list.get(0);
      if (list.size() > 1) return element;
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
    if (elements == null) return myMatchingVisitor.isLeftLooseMatching() || elements2 == null;

    return myMatchingVisitor.matchInAnyOrder(
      elements.getReferenceElements(),
      (elements2 != null) ? elements2.getReferenceElements() : PsiElement.EMPTY_ARRAY
    );
  }

  private boolean compareClasses(final PsiClass clazz, final PsiClass clazz2) {
    final PsiClass saveClazz = this.myClazz;
    this.myClazz = clazz2;
    final MatchContext context = myMatchingVisitor.getMatchContext();
    final JavaCompiledPattern javaPattern = (JavaCompiledPattern)context.getPattern();

    final Set<PsiElement> matchedElements = new THashSet<>();
    context.pushMatchedElementsListener(elements -> matchedElements.addAll(elements));
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
          unmatchedSubstitutionHandler = javaPattern.getHandler(element);
          break;
        }
      }
      if (unmatchedSubstitutionHandler instanceof SubstitutionHandler) {
        final SubstitutionHandler handler = (SubstitutionHandler)unmatchedSubstitutionHandler;
        for (PsiElement element : unmatchedElements) {
          handler.handle(element, context);
        }
      } else {
        clazz2.putUserData(GlobalMatchingVisitor.UNMATCHED_ELEMENTS_KEY, unmatchedElements);
      }

      return true;
    }
    finally {
      this.myClazz = saveClazz;
      context.popMatchedElementsListener();
    }
  }

  private boolean matchBody(PsiElement patternElement, PsiElement matchElement) {
    if (myMatchingVisitor.getMatchContext().getOptions().isLooseMatching()) {
      if (matchElement instanceof PsiBlockStatement) {
        final PsiCodeBlock codeBlock = ((PsiBlockStatement)matchElement).getCodeBlock();
        if (patternElement instanceof PsiBlockStatement || codeBlock.getStatementCount() == 1) {
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
    final PsiArrayAccessExpression other = getExpression(PsiArrayAccessExpression.class);
    if (other != null) {
      myMatchingVisitor.setResult(myMatchingVisitor.match(slice.getArrayExpression(), other.getArrayExpression()) &&
                                  myMatchingVisitor.match(slice.getIndexExpression(), other.getIndexExpression()));
    }
  }

  @Override
  public void visitMethodReferenceExpression(PsiMethodReferenceExpression expression) {
    if (getExpression(PsiMethodReferenceExpression.class) == null) return;
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

    final PsiElement other = myMatchingVisitor.getElement();
    final PsiExpression qualifier = reference.getQualifierExpression();
    if (_handler instanceof SubstitutionHandler && (qualifier == null || special)) {
      final SubstitutionHandler handler = (SubstitutionHandler)_handler;
      if (handler.isSubtype() || handler.isStrictSubtype()) {
        if (myMatchingVisitor.setResult(checkMatchWithinHierarchy(reference, other, handler))) {
          handler.addResult(other, 0, -1, myMatchingVisitor.getMatchContext());
        }
      }
      else {
        final PsiElement deparenthesized = other instanceof PsiExpression && context.getOptions().isLooseMatching() ?
                                           PsiUtil.skipParenthesizedExprDown((PsiExpression)other) : other;
        myMatchingVisitor.setResult(handler.validate(deparenthesized, 0, -1, context));
        if (myMatchingVisitor.getResult()) {
          handler.addResult(other, 0, -1, context);
        }
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
        if (!myMatchingVisitor.setResult(myMatchingVisitor.handleTypedElement(referenceElement, referenceElement2))) return;
      }
      else {
        if (!myMatchingVisitor.setResult(myMatchingVisitor.matchText(referenceElement, referenceElement2))) return;
      }

      if (!myMatchingVisitor.setResult(qualifier instanceof PsiThisExpression && qualifier2 == null ||
                                       myMatchingVisitor.matchOptionally(qualifier, qualifier2))) return;
      if (qualifier2 == null) myMatchingVisitor.setResult(matchImplicitQualifier(qualifier, other, context));
    }
    else {
      myMatchingVisitor.setResult(false);
    }
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
    PsiTypeElement typeElement = (PsiTypeElement)element;
    if (typeElement.getType() instanceof PsiDisjunctionType) {
      // getInnermostComponentReferenceElement() doesn't make sense for disjunction type
      return typeElement;
    }
    if (typeElement.isInferredType()) {
      // replace inferred type with explicit type if possible
      final PsiType type = typeElement.getType();
      if (type == PsiType.NULL) {
        return typeElement;
      }
      final String canonicalText = type.getCanonicalText();
      typeElement = JavaPsiFacade.getElementFactory(typeElement.getProject()).createTypeElementFromText(canonicalText, typeElement);
    }
    final PsiJavaCodeReferenceElement referenceElement = typeElement.getInnermostComponentReferenceElement();
    return (referenceElement != null) ? referenceElement : getInnermostComponentTypeElement(typeElement);
  }

  private static PsiTypeElement[] getTypeParameters(PsiJavaCodeReferenceElement referenceElement, Boolean replaceDiamondWithExplicitTypes) {
    final PsiReferenceParameterList referenceElementParameterList = referenceElement.getParameterList();
    if (referenceElementParameterList == null) {
      return null;
    }
    final PsiTypeElement[] typeParameterElements = referenceElementParameterList.getTypeParameterElements();
    if (typeParameterElements.length != 1 || replaceDiamondWithExplicitTypes == Boolean.FALSE) {
      return typeParameterElements;
    }
    final PsiType type = typeParameterElements[0].getType();
    if (!(type instanceof PsiDiamondType)) {
      return typeParameterElements;
    }
    if (replaceDiamondWithExplicitTypes == null) {
      return null;
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

  private Boolean shouldReplaceDiamondWithExplicitTypes(PsiElement element) {
    if (!(element instanceof PsiJavaCodeReferenceElement)) {
      return Boolean.TRUE;
    }
    final PsiJavaCodeReferenceElement javaCodeReferenceElement = (PsiJavaCodeReferenceElement)element;
    final PsiReferenceParameterList parameterList = javaCodeReferenceElement.getParameterList();
    if (parameterList == null) {
      return Boolean.TRUE;
    }
    final PsiTypeElement[] elements = parameterList.getTypeParameterElements();
    if (elements.length != 1) {
      return Boolean.TRUE;
    }
    final PsiTypeElement typeElement = elements[0];
    final MatchingHandler handler = myMatchingVisitor.getMatchContext().getPattern().getHandler(typeElement);
    if (handler instanceof SubstitutionHandler) {
      final SubstitutionHandler substitutionHandler = (SubstitutionHandler)handler;
      if (substitutionHandler.getMinOccurs() > 0) {
        return null;
      }
    }
    return Boolean.valueOf(!(typeElement.getType() instanceof PsiDiamondType));
  }

  private boolean matchType(final PsiElement patternType, final PsiElement matchedType) {
    PsiElement patternElement = getInnermostComponent(patternType);
    PsiElement matchedElement = patternElement instanceof PsiTypeElement && ((PsiTypeElement)patternElement).isInferredType()
                                ? matchedType
                                : getInnermostComponent(matchedType);

    PsiElement[] typeParameters = null;
    if (matchedElement instanceof PsiJavaCodeReferenceElement) {
      final PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement)matchedElement;
      typeParameters = getTypeParameters(referenceElement, shouldReplaceDiamondWithExplicitTypes(patternElement));
    }
    else if (matchedElement instanceof PsiTypeParameter) {
      matchedElement = ((PsiTypeParameter)matchedElement).getNameIdentifier();
    }
    else if (matchedElement instanceof PsiClass && ((PsiClass)matchedElement).hasTypeParameters()) {
      typeParameters = ((PsiClass)matchedElement).getTypeParameters();
      matchedElement = ((PsiClass)matchedElement).getNameIdentifier();
    }
    else if (matchedElement instanceof PsiMethod && ((PsiMethod)matchedElement).hasTypeParameters()) {
      typeParameters = ((PsiMethod)matchedElement).getTypeParameters();
      matchedElement = ((PsiMethod)matchedElement).getNameIdentifier();
    }

    if (patternElement instanceof PsiTypeElement && matchedElement instanceof PsiTypeElement) {
      final PsiType type1 = ((PsiTypeElement)patternElement).getType();
      final PsiType type2 = ((PsiTypeElement)matchedElement).getType();
      if (type1 instanceof PsiWildcardType && type2 instanceof PsiWildcardType) {
        final PsiWildcardType wildcardType1 = (PsiWildcardType)type1;
        final PsiWildcardType wildcardType2 = (PsiWildcardType)type2;
        if (wildcardType1.equals(wildcardType2)) return true;
        if (wildcardType1.isExtends() && (wildcardType2.isExtends() || !wildcardType2.isBounded())) {
          if (wildcardType2.isExtends()) {
            return myMatchingVisitor.match(patternElement.getLastChild(), matchedElement.getLastChild());
          }
          else if (!wildcardType2.isBounded()) {
            return myMatchingVisitor.matchOptionally(patternElement.getLastChild(), null);
          }
        }
        else if (wildcardType1.isSuper() && wildcardType2.isSuper()) {
          return myMatchingVisitor.match(patternElement.getLastChild(), matchedElement.getLastChild());
        }
      }
    }
    if (patternElement instanceof PsiJavaCodeReferenceElement) {
      final PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement)patternElement;
      final PsiReferenceParameterList list = referenceElement.getParameterList();
      boolean typeParametersMatched = false;
      if (list != null) {
        final PsiTypeElement[] elements = list.getTypeParameterElements();
        if (elements.length > 0) {
          typeParametersMatched = true;
          if (!myMatchingVisitor.matchSequentially(elements, (typeParameters == null) ? PsiElement.EMPTY_ARRAY : typeParameters)) {
            return false;
          }
        }
      }
      patternElement = referenceElement.getReferenceNameElement();
      if (typeParametersMatched && matchedElement instanceof PsiJavaCodeReferenceElement) {
        matchedElement = ((PsiJavaCodeReferenceElement)matchedElement).getReferenceNameElement();
      }
    }

    final int matchedArrayDimensions = getArrayDimensions(matchedType);
    final int patternArrayDimensions = getArrayDimensions(patternType);

    if (myMatchingVisitor.getMatchContext().getPattern().isTypedVar(patternElement)) {
      final SubstitutionHandler handler = (SubstitutionHandler)myMatchingVisitor.getMatchContext().getPattern().getHandler(patternElement);

      RegExpPredicate regExpPredicate = null;

      boolean fullTypeResult = false;
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
        fullTypeResult = true;
      }

      try {
        final boolean result = (handler.isSubtype() || handler.isStrictSubtype())
                               ? checkMatchWithinHierarchy(patternElement, matchedElement, handler)
                               : handler.validate(matchedElement, 0, -1, myMatchingVisitor.getMatchContext());
        if (result) handler.addResult(fullTypeResult ? matchedType : matchedElement, 0, -1, myMatchingVisitor.getMatchContext());
        return result;
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

  private boolean checkMatchWithinHierarchy(PsiElement patternElement, PsiElement matchElement, SubstitutionHandler handler) {
    boolean includeInterfaces = true;
    boolean includeClasses = true;
    final PsiElement patternParent = patternElement.getParent();

    if (patternParent instanceof PsiReferenceList) {
      final PsiElement patternGrandParent = patternParent.getParent();

      if (patternGrandParent instanceof PsiClass) {
        final PsiClass psiClass = (PsiClass)patternGrandParent;

        if (patternParent == psiClass.getExtendsList()) {
          includeInterfaces = psiClass.isInterface();
        }
        else if (patternParent == psiClass.getImplementsList()) {
          includeClasses = false;
        }
      }
    }

    final NodeIterator nodes = new HierarchyNodeIterator(matchElement, includeClasses, includeInterfaces);
    if (handler.isStrictSubtype()) {
      nodes.advance();
    }

    final boolean negated = handler.getPredicate() instanceof NotPredicate;
    while (nodes.hasNext() && negated == handler.validate(nodes.current(), 0, -1, myMatchingVisitor.getMatchContext())) {
      nodes.advance();
    }
    return negated != nodes.hasNext();
  }

  @Override
  public void visitConditionalExpression(final PsiConditionalExpression cond) {
    final PsiConditionalExpression cond2 = getExpression(PsiConditionalExpression.class);
    if (cond2 == null) return;
    myMatchingVisitor.setResult(myMatchingVisitor.match(cond.getCondition(), cond2.getCondition()) &&
                                myMatchingVisitor.matchSons(cond, cond2));
  }

  @Override
  public void visitPolyadicExpression(PsiPolyadicExpression expression) {
    final PsiPolyadicExpression expr2 = getExpression(PsiPolyadicExpression.class);
    if (expr2 == null) return;
    if (myMatchingVisitor.setResult(expression.getOperationTokenType().equals(expr2.getOperationTokenType()))) {
      myMatchingVisitor.setResult(myMatchingVisitor.matchSequentially(expression.getOperands(), expr2.getOperands()));
    }
  }

  @Override
  public void visitVariable(final PsiVariable var) {
    final PsiVariable var2 = (PsiVariable)myMatchingVisitor.getElement();

    final MatchContext context = myMatchingVisitor.getMatchContext();
    final PsiIdentifier nameIdentifier = var.getNameIdentifier();
    final boolean isTypedVar = context.getPattern().isTypedVar(nameIdentifier);
    context.pushResult();
    try {
      if (!myMatchingVisitor.setResult((isTypedVar || myMatchingVisitor.matchText(nameIdentifier, var2.getNameIdentifier())) &&
                                       myMatchingVisitor.match(var.getModifierList(), var2.getModifierList()))) return;
      final PsiTypeElement typeElement1 = var.getTypeElement();
      if (typeElement1 != null) {
        PsiTypeElement typeElement2 = var2.getTypeElement();
        if (typeElement2 == null) {
          typeElement2 = JavaPsiFacade.getElementFactory(var2.getProject()).createTypeElement(var2.getType());
        }
        if (!myMatchingVisitor.setResult(myMatchingVisitor.match(typeElement1, typeElement2))) return;
      }

      // Check initializer
      final PsiExpression initializer = var.getInitializer();
      final PsiExpression var2Initializer = var2.getInitializer();
      myMatchingVisitor.setResult(myMatchingVisitor.matchOptionally(initializer, var2Initializer));
    }
    finally {
      myMatchingVisitor.scopeMatch(nameIdentifier, isTypedVar, var2.getNameIdentifier());
    }
  }

  private void matchArrayOrArguments(final PsiNewExpression new1, final PsiNewExpression new2) {
    final PsiExpression[] dimensions1 = new1.getArrayDimensions();
    final PsiExpression[] dimensions2 = new2.getArrayDimensions();

    if (!myMatchingVisitor.setResult(myMatchingVisitor.matchSons(new1.getArrayInitializer(), new2.getArrayInitializer()))) return;
    if (!myMatchingVisitor.setResult(dimensions1.length == dimensions2.length)) return;
    if (dimensions1.length != 0) {
      for (int i = 0; i < dimensions1.length; ++i) {
        if (!myMatchingVisitor.setResult(myMatchingVisitor.match(dimensions1[i], dimensions2[i]))) return;
      }
    }
    else {
      final PsiType type1 = new1.getType();
      final PsiType type2 = new2.getType();
      myMatchingVisitor.setResult(type1 != null && type2 != null && type1.getArrayDimensions() == type2.getArrayDimensions() &&
                                  myMatchingVisitor.matchSons(new1.getArgumentList(), new2.getArgumentList()) &&
                                  myMatchingVisitor.setResult(matchTypeParameters(new1, new2)));
    }
  }

  private static boolean matchImplicitQualifier(PsiExpression qualifier, PsiElement reference, MatchContext context) {
    final PsiElement target = reference instanceof PsiMethodCallExpression
                              ? ((PsiMethodCallExpression)reference).resolveMethod()
                              : ((PsiReference)reference).resolve();
    if (target instanceof PsiMember && qualifier instanceof PsiThisExpression) {
      return !((PsiMember)target).hasModifierProperty(PsiModifier.STATIC) &&
             (target instanceof PsiField || target instanceof PsiMethod);
    }
    final MatchingHandler matchingHandler = context.getPattern().getHandler(qualifier);
    if (!(matchingHandler instanceof SubstitutionHandler)) {
      return false;
    }
    final SubstitutionHandler substitutionHandler = (SubstitutionHandler)matchingHandler;
    if (target instanceof PsiModifierListOwner && ((PsiModifierListOwner)target).hasModifierProperty(PsiModifier.STATIC)) {
      return substitutionHandler.handle(PsiTreeUtil.getParentOfType(target, PsiClass.class), context);
    } else {
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(reference.getProject());
      final PsiExpression implicitReference = factory.createExpressionFromText("this", reference);
      return substitutionHandler.handle(implicitReference, context);
    }
  }

  @Override
  public void visitMethodCallExpression(final PsiMethodCallExpression mcall) {
    final PsiMethodCallExpression mcall2 = getExpression(PsiMethodCallExpression.class);
    if (mcall2 == null) return;
    final PsiReferenceExpression mcallRef1 = mcall.getMethodExpression();
    final PsiReferenceExpression mcallRef2 = mcall2.getMethodExpression();

    final PsiElement patternMethodName = mcallRef1.getReferenceNameElement();
    final MatchContext context = myMatchingVisitor.getMatchContext();
    final boolean isTypedVar = context.getPattern().isTypedVar(patternMethodName);
    if (!isTypedVar && !myMatchingVisitor.setResult(myMatchingVisitor.matchText(patternMethodName, mcallRef2.getReferenceNameElement()))) {
      return;
    }

    final PsiExpression patternQualifier = mcallRef1.getQualifierExpression();
    final PsiExpression matchedQualifier = mcallRef2.getQualifierExpression();
    if (!myMatchingVisitor.setResult(patternQualifier instanceof PsiThisExpression && matchedQualifier == null ||
                                     myMatchingVisitor.matchOptionally(patternQualifier, matchedQualifier))) return;
    if (patternQualifier != null && matchedQualifier == null) {
      if (!myMatchingVisitor.setResult(matchImplicitQualifier(patternQualifier, mcall2, context))) return;
    }

    if (!myMatchingVisitor.setResult(myMatchingVisitor.matchSons(mcall.getArgumentList(), mcall2.getArgumentList()))) return;
    if (!myMatchingVisitor.setResult(matchTypeParameters(mcall, mcall2))) return;
    if (isTypedVar) {
      myMatchingVisitor.setResult(myMatchingVisitor.handleTypedElement(patternMethodName, mcallRef2.getReferenceNameElement()));
    }
  }

  private boolean matchTypeParameters(PsiCallExpression call1, PsiCallExpression call2) {
    final PsiReferenceParameterList patternParameterList = call1.getTypeArgumentList();
    final PsiTypeElement[] patternTypeElements = patternParameterList.getTypeParameterElements();
    if (patternTypeElements.length == 0) {
      return true;
    }
    PsiReferenceParameterList matchedParameterList = call2.getTypeArgumentList();
    if (matchedParameterList.getFirstChild() == null && myMatchingVisitor.getMatchContext().getOptions().isLooseMatching()) {
      // check inferred type parameters
      final JavaResolveResult resolveResult = call2.resolveMethodGenerics();
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
        final PsiTypeElement matchedTypeElement = JavaPsiFacade.getElementFactory(call1.getProject()).createTypeElement(type);
        matchedParameterList.add(matchedTypeElement);
      }
    }
    final PsiTypeElement[] matchedTypeElements = matchedParameterList.getTypeParameterElements();
    return myMatchingVisitor.matchSequentially(patternTypeElements, matchedTypeElements);
  }

  @Override
  public void visitExpressionStatement(final PsiExpressionStatement expr) {
    final PsiElement other = myMatchingVisitor.getElement();
    if (myMatchingVisitor.setResult(other instanceof PsiExpressionStatement)) {
      final PsiExpressionStatement expr2 = (PsiExpressionStatement)other;
      myMatchingVisitor.setResult(myMatchingVisitor.match(expr.getExpression(), expr2.getExpression()));
    }
  }

  @Override
  public void visitLiteralExpression(final PsiLiteralExpression const1) {
    final PsiLiteralExpression const2 = getExpression(PsiLiteralExpression.class);
    if (const2 == null) return;
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
    final PsiAssignmentExpression other = getExpression(PsiAssignmentExpression.class);
    if (other != null) {
      myMatchingVisitor.setResult(assign.getOperationTokenType().equals(other.getOperationTokenType()) &&
                                  myMatchingVisitor.match(assign.getLExpression(), other.getLExpression()) &&
                                  myMatchingVisitor.match(assign.getRExpression(), other.getRExpression()));
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

    if (myMatchingVisitor.setResult(myMatchingVisitor.match(switch1.getExpression(), switch2.getExpression()))) {
      final List<PsiSwitchLabelStatement> cases1 = PsiTreeUtil.getChildrenOfTypeAsList(switch1.getBody(), PsiSwitchLabelStatement.class);
      if (cases1.isEmpty()) {
        myMatchingVisitor.setResult(myMatchingVisitor.matchSons(switch1.getBody(), switch2.getBody()));
      }
      else {
        final List<PsiSwitchLabelStatement> cases2 = PsiTreeUtil.getChildrenOfTypeAsList(switch2.getBody(), PsiSwitchLabelStatement.class);
        myMatchingVisitor.setResult(
          myMatchingVisitor.matchSequentially(cases1.toArray(PsiElement.EMPTY_ARRAY), cases2.toArray(PsiElement.EMPTY_ARRAY)));
      }
    }
  }

  @Override
  public void visitSwitchLabelStatement(final PsiSwitchLabelStatement case1) {
    final PsiSwitchLabelStatement case2 = (PsiSwitchLabelStatement)myMatchingVisitor.getElement();
    final MatchContext context = myMatchingVisitor.getMatchContext();
    final PsiExpression value1 = case1.getCaseValue();
    final PsiExpression value2 = case2.getCaseValue();
    final boolean isTypedVar = context.getPattern().isTypedVar(value1);
    context.pushResult();
    try {
      if (myMatchingVisitor.setResult(isTypedVar ||
                                      case1.isDefaultCase() == case2.isDefaultCase() && myMatchingVisitor.match(value1, value2))) {
        final List<PsiStatement> statements = collectCaseStatements(case1);
        if (!statements.isEmpty()) {
          myMatchingVisitor.setResult(myMatchingVisitor.matchSequentially(statements.toArray(PsiElement.EMPTY_ARRAY),
                                                                          collectCaseStatements(case2).toArray(PsiElement.EMPTY_ARRAY)));
        }
      }
    } finally {
      myMatchingVisitor.scopeMatch(value1, isTypedVar, (value2 == null) ? case2 : value2);
    }
  }

  private static List<PsiStatement> collectCaseStatements(PsiSwitchLabelStatement switchLabelStatement) {
    final List<PsiStatement> result = new SmartList<>();
    PsiStatement sibling = PsiTreeUtil.getNextSiblingOfType(switchLabelStatement, PsiStatement.class);
    while (sibling != null && !(sibling instanceof PsiSwitchLabelStatement)) {
      result.add(sibling);
      sibling = PsiTreeUtil.getNextSiblingOfType(sibling, PsiStatement.class);
    }
    return result;
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

    myMatchingVisitor.setResult(myMatchingVisitor.matchOptionally(return1.getReturnValue(), return2.getReturnValue()));
  }

  @Override
  public void visitPostfixExpression(final PsiPostfixExpression postfix) {
    final PsiPostfixExpression postfix2 = getExpression(PsiPostfixExpression.class);
    if (postfix2 == null) return;
    myMatchingVisitor.setResult(postfix.getOperationTokenType().equals(postfix2.getOperationTokenType()) &&
                                myMatchingVisitor.match(postfix.getOperand(), postfix2.getOperand()));
  }

  @Override
  public void visitPrefixExpression(final PsiPrefixExpression prefix) {
    final PsiPrefixExpression prefix2 = getExpression(PsiPrefixExpression.class);
    if (prefix2 == null) return;
    myMatchingVisitor.setResult(prefix.getOperationTokenType().equals(prefix2.getOperationTokenType()) &&
                                myMatchingVisitor.match(prefix.getOperand(), prefix2.getOperand()));
  }

  @Override
  public void visitAssertStatement(final PsiAssertStatement assert1) {
    final PsiAssertStatement assert2 = (PsiAssertStatement)myMatchingVisitor.getElement();

    myMatchingVisitor.setResult(myMatchingVisitor.match(assert1.getAssertCondition(), assert2.getAssertCondition()) &&
                                myMatchingVisitor.matchOptionally(assert1.getAssertDescription(), assert2.getAssertDescription()));
  }

  @Override
  public void visitBreakStatement(final PsiBreakStatement break1) {
    final PsiBreakStatement break2 = (PsiBreakStatement)myMatchingVisitor.getElement();

    myMatchingVisitor.setResult(myMatchingVisitor.matchOptionally(break1.getLabelIdentifier(), break2.getLabelIdentifier()));
  }

  @Override
  public void visitContinueStatement(final PsiContinueStatement continue1) {
    final PsiContinueStatement continue2 = (PsiContinueStatement)myMatchingVisitor.getElement();

    myMatchingVisitor.setResult(myMatchingVisitor.matchOptionally(continue1.getLabelIdentifier(), continue2.getLabelIdentifier()));
  }

  @Override
  public void visitSuperExpression(final PsiSuperExpression super1) {
    getExpression(PsiSuperExpression.class);
  }

  @Override
  public void visitThisExpression(final PsiThisExpression this1) {
    getExpression(PsiThisExpression.class);
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
    final PsiElement other = myMatchingVisitor.getElement();
    if (myMatchingVisitor.setResult(other instanceof PsiParenthesizedExpression)) {
      myMatchingVisitor.setResult(myMatchingVisitor.matchSons(expr, other));
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

    if (!myMatchingVisitor.setResult(myMatchingVisitor.matchSons(try1.getTryBlock(), try2.getTryBlock()))) return;

    final PsiResourceList resourceList1 = try1.getResourceList();
    final PsiCatchSection[] catches1 = try1.getCatchSections();
    final PsiCodeBlock finally1 = try1.getFinallyBlock();

    final PsiResourceList resourceList2 = try2.getResourceList();
    final PsiCatchSection[] catches2 = try2.getCatchSections();
    final PsiCodeBlock finally2 = try2.getFinallyBlock();

    final MatchContext context = myMatchingVisitor.getMatchContext();
    if (!context.getOptions().isLooseMatching() &&
        ((catches1.length == 0 && catches2.length != 0) ||
         (finally1 == null && finally2 != null) ||
         (resourceList1 == null && resourceList2 != null))
      ) {
      myMatchingVisitor.setResult(false);
    }
    else {
      final List<PsiElement> unmatchedElements = new SmartList<>();
      if (resourceList1 != null) {
        final List<PsiResourceListElement> resources1 = PsiTreeUtil.getChildrenOfTypeAsList(resourceList1, PsiResourceListElement.class);
        final List<PsiResourceListElement> resources2 = PsiTreeUtil.getChildrenOfTypeAsList(resourceList2, PsiResourceListElement.class);
        if (!myMatchingVisitor.setResult(myMatchingVisitor.matchInAnyOrder(resources1.toArray(PsiElement.EMPTY_ARRAY),
                                                                           resources2.toArray(PsiElement.EMPTY_ARRAY)))) {
          return;
        }
      }
      else if (resourceList2 != null){
        unmatchedElements.add(resourceList2);
      }

      ContainerUtil.addAll(unmatchedElements, catches2);
      context.pushMatchedElementsListener(elements -> unmatchedElements.removeAll(elements));
      try {
        myMatchingVisitor.setResult(myMatchingVisitor.matchInAnyOrder(catches1, catches2));
      } finally {
        context.popMatchedElementsListener();
      }

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
  public void visitResourceExpression(PsiResourceExpression expression) {
    final PsiElement other = myMatchingVisitor.getElement();
    myMatchingVisitor.setResult(other instanceof PsiResourceExpression &&
                                myMatchingVisitor.match(expression.getExpression(), ((PsiResourceExpression)other).getExpression()));
  }

  @Override
  public void visitLabeledStatement(PsiLabeledStatement statement) {
    final PsiLabeledStatement other = (PsiLabeledStatement)myMatchingVisitor.getElement();
    final PsiIdentifier identifier = statement.getLabelIdentifier();
    final MatchContext context = myMatchingVisitor.getMatchContext();
    final boolean isTypedVar = context.getPattern().isTypedVar(identifier);
    context.pushResult();
    try {
      myMatchingVisitor.setResult((isTypedVar || myMatchingVisitor.matchText(identifier, other.getNameIdentifier())) &&
                                  myMatchingVisitor.match(statement.getStatement(), other.getStatement()));
    }
    finally {
      myMatchingVisitor.scopeMatch(identifier, isTypedVar, other.getNameIdentifier());
    }
  }

  @Override
  public void visitInstanceOfExpression(final PsiInstanceOfExpression instanceOf) {
    final PsiInstanceOfExpression other = getExpression(PsiInstanceOfExpression.class);
    if (other == null) return;
    if (!myMatchingVisitor.setResult(myMatchingVisitor.match(instanceOf.getOperand(), other.getOperand()))) return;
    myMatchingVisitor.setResult(myMatchingVisitor.match(instanceOf.getCheckType(), other.getCheckType()));
  }

  @Override
  public void visitNewExpression(final PsiNewExpression new1) {
    final PsiExpression other = getExpression();
    final PsiJavaCodeReferenceElement classReference = new1.getClassReference();
    if (other instanceof PsiArrayInitializerExpression &&
        other.getParent() instanceof PsiVariable &&
        new1.getArrayDimensions().length == 0 &&
        new1.getArrayInitializer() != null
      ) {
      final MatchContext matchContext = myMatchingVisitor.getMatchContext();
      final CompiledPattern pattern = matchContext.getPattern();
      final boolean isTypedVar = pattern.isTypedVar(classReference);
      if (isTypedVar && !myMatchingVisitor.setResult(myMatchingVisitor.matchOptionally(classReference, null))) {
        return;
      }
      final PsiType otherType = other.getType();
      final MatchingHandler handler;
      if (classReference != null && (handler = pattern.getHandler(classReference)) instanceof SubstitutionHandler && otherType != null) {
        final SubstitutionHandler substitutionHandler = (SubstitutionHandler)handler;
        final MatchPredicate predicate = substitutionHandler.getPredicate();
        if (predicate != null) {
          final PsiElementFactory factory = JavaPsiFacade.getElementFactory(other.getProject());
          final PsiTypeElement otherTypeElement = factory.createTypeElement(otherType.getDeepComponentType());
          myMatchingVisitor.setResult(predicate.match(otherTypeElement, matchContext));
        }
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

    if (!myMatchingVisitor.setResult(other instanceof PsiNewExpression)) {
      return;
    }
    final PsiNewExpression new2 = (PsiNewExpression)other;

    if (classReference != null) {
      if (new2.getClassReference() != null) {
        if (myMatchingVisitor.setResult(myMatchingVisitor.matchOptionally(classReference, new2.getClassReference()))) {
          matchArrayOrArguments(new1, new2);
        }
        return;
      }
      else {
        // match array of primitive by new 'T();
        final PsiKeyword newKeyword = PsiTreeUtil.getChildOfType(new2, PsiKeyword.class);
        final PsiElement element = PsiTreeUtil.getNextSiblingOfType(newKeyword, PsiWhiteSpace.class);

        if (element != null && element.getNextSibling() instanceof PsiKeyword) {
          if (myMatchingVisitor.setResult(myMatchingVisitor.matchOptionally(classReference, element.getNextSibling()))) {
            matchArrayOrArguments(new1, new2);
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
    final PsiTypeCastExpression other = getExpression(PsiTypeCastExpression.class);
    if (other != null) {
      myMatchingVisitor.setResult(myMatchingVisitor.match(cast.getCastType(), other.getCastType()) &&
                                  myMatchingVisitor.match(cast.getOperand(), other.getOperand()));
    }
  }

  @Override
  public void visitClassObjectAccessExpression(final PsiClassObjectAccessExpression expr) {
    final PsiClassObjectAccessExpression other = getExpression(PsiClassObjectAccessExpression.class);
    if (other != null) {
      myMatchingVisitor.setResult(myMatchingVisitor.match(expr.getOperand(), other.getOperand()));
    }
  }

  @Override
  public void visitReferenceElement(final PsiJavaCodeReferenceElement ref) {
    final PsiElement other = myMatchingVisitor.getElement();
    final PsiAnnotation[] annotations = PsiTreeUtil.getChildrenOfType(ref, PsiAnnotation.class);
    if (annotations != null) {
      final PsiAnnotation[] otherAnnotations = PsiTreeUtil.getChildrenOfType(other, PsiAnnotation.class);
      if (!myMatchingVisitor.setResult(otherAnnotations != null && myMatchingVisitor.matchInAnyOrder(annotations, otherAnnotations))) {
        return;
      }
    }
    myMatchingVisitor.setResult(matchType(ref, other));
  }

  @Override
  public void visitTypeElement(final PsiTypeElement typeElement) {
    final PsiElement other = myMatchingVisitor.getElement(); // might not be a PsiTypeElement

    final PsiAnnotation[] annotations = PsiTreeUtil.getChildrenOfType(typeElement, PsiAnnotation.class);
    // can't use AnnotationOwner api because it is not implemented completely yet (see e.g. ClsTypeParameterImpl)
    if (annotations != null) {
      final PsiAnnotation[] annotations2 = PsiTreeUtil.getChildrenOfType(other, PsiAnnotation.class);
      if (!myMatchingVisitor.setResult(annotations2 != null && myMatchingVisitor.matchInAnyOrder(annotations, annotations2))) return;
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
      if (!myMatchingVisitor.setResult(((SubstitutionHandler)handler).handle(identifier2, myMatchingVisitor.getMatchContext()))) return;
    }
    else if (!myMatchingVisitor.setResult(myMatchingVisitor.matchText(identifier, identifier2))) return;

    if (!myMatchingVisitor.setResult(matchInAnyOrder(psiTypeParameter.getExtendsList(), parameter.getExtendsList()))) return;
    myMatchingVisitor.setResult(myMatchingVisitor.matchInAnyOrder(psiTypeParameter.getAnnotations(), parameter.getAnnotations()));
  }

  @Override
  public void visitClass(PsiClass clazz) {
    final PsiClass clazz2 = (PsiClass)myMatchingVisitor.getElement();
    if (clazz.hasTypeParameters()) {
      if (!myMatchingVisitor.setResult(myMatchingVisitor.match(clazz.getTypeParameterList(), clazz2.getTypeParameterList()))) return;
    }

    final PsiDocComment comment = clazz.getDocComment();
    if (comment != null) {
      if (!myMatchingVisitor.setResult(myMatchingVisitor.match(comment, clazz2))) return;
    }

    final PsiIdentifier identifier = clazz.getNameIdentifier();
    final boolean isTypedVar = myMatchingVisitor.getMatchContext().getPattern().isTypedVar(identifier);

    if (clazz.getModifierList().getTextLength() > 0) {
      if (!myMatchingVisitor.match(clazz.getModifierList(), clazz2.getModifierList())) {
        myMatchingVisitor.setResult(false);
        return;
      }
    }

    if (myMatchingVisitor.setResult((isTypedVar || myMatchingVisitor.matchText(identifier, clazz2.getNameIdentifier())) &&
                                    compareClasses(clazz, clazz2)) && isTypedVar) {
      PsiElement id = clazz2.getNameIdentifier();
      if (id == null) id = clazz2;
      final SubstitutionHandler handler = (SubstitutionHandler)myMatchingVisitor.getMatchContext().getPattern().getHandler(identifier);
      if (handler.isSubtype() || handler.isStrictSubtype()) {
        if (myMatchingVisitor.setResult(checkMatchWithinHierarchy(identifier, id, handler))) {
          handler.addResult(id, 0, -1, myMatchingVisitor.getMatchContext());
        }
      }
      else {
        myMatchingVisitor.setResult(myMatchingVisitor.handleTypedElement(identifier, id));
      }
    }
  }

  @Override
  public void visitTypeParameterList(PsiTypeParameterList psiTypeParameterList) {
    myMatchingVisitor.setResult(myMatchingVisitor.matchSequentially(psiTypeParameterList.getFirstChild(),
                                                                    myMatchingVisitor.getElement().getFirstChild()));
  }

  @Override
  public void visitMethod(PsiMethod method) {
    final PsiIdentifier methodNameNode = method.getNameIdentifier();
    final MatchContext context = myMatchingVisitor.getMatchContext();
    final boolean isTypedVar = context.getPattern().isTypedVar(methodNameNode);
    final PsiMethod method2 = (PsiMethod)myMatchingVisitor.getElement();

    context.pushResult();

    try {
      final PsiDocComment docComment = method.getDocComment();
      if (docComment != null && !myMatchingVisitor.setResult(myMatchingVisitor.match(docComment, method2))) return;
      if (method.hasTypeParameters() && !myMatchingVisitor.setResult(
        myMatchingVisitor.match(method.getTypeParameterList(), ((PsiMethod)myMatchingVisitor.getElement()).getTypeParameterList()))) return;

      if (!checkHierarchy(method2, method)) {
        myMatchingVisitor.setResult(false);
        return;
      }

      myMatchingVisitor.setResult(method.isConstructor() == method2.isConstructor() &&
                                  (isTypedVar || myMatchingVisitor.matchText(methodNameNode, method2.getNameIdentifier())) &&
                                  myMatchingVisitor.match(method.getModifierList(), method2.getModifierList()) &&
                                  myMatchingVisitor.matchSons(method.getParameterList(), method2.getParameterList()) &&
                                  myMatchingVisitor.match(method.getReturnTypeElement(), method2.getReturnTypeElement()) &&
                                  matchInAnyOrder(method.getThrowsList(), method2.getThrowsList()) &&
                                  myMatchingVisitor.matchSonsOptionally(method.getBody(), method2.getBody()));
    }
    finally {
      myMatchingVisitor.scopeMatch(methodNameNode, isTypedVar, method2.getNameIdentifier());
    }
  }

  @SuppressWarnings("unchecked")
  private <T extends PsiExpression> T getExpression(Class<T> aClass) {
    final PsiExpression other = getExpression();
    return myMatchingVisitor.setResult(aClass.isInstance(other)) ? (T)other : null;
  }

  private PsiExpression getExpression() {
    final PsiElement other = myMatchingVisitor.getElement();
    return (other instanceof PsiExpression) ? PsiUtil.skipParenthesizedExprDown((PsiExpression)other) : null;
  }
}

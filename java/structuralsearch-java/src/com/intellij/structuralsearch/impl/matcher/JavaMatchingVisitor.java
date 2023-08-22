// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch.impl.matcher;

import com.intellij.dupLocator.iterators.ArrayBackedNodeIterator;
import com.intellij.dupLocator.iterators.NodeIterator;
import com.intellij.dupLocator.iterators.SingleNodeIterator;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.JavaPsiPatternUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.structuralsearch.MatchOptions;
import com.intellij.structuralsearch.MatchUtil;
import com.intellij.structuralsearch.StructuralSearchUtil;
import com.intellij.structuralsearch.impl.matcher.handlers.LiteralWithSubstitutionHandler;
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
import com.siyeh.ig.psiutils.InstanceOfUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    myMatchingVisitor = matchingVisitor;
  }

  @Override
  public void visitComment(@NotNull PsiComment comment) {
    PsiComment other = null;

    final PsiElement element = myMatchingVisitor.getElement();
    if (!(element instanceof PsiComment)) {
      if (element instanceof PsiMember && PsiTreeUtil.skipWhitespacesAndCommentsForward(comment) instanceof PsiDeclarationStatement) {
        other = ObjectUtils.tryCast(element.getFirstChild(), PsiComment.class);
      }
    }
    else {
      other = (PsiComment)element;
    }

    if (!myMatchingVisitor.setResult(other != null)) {
      return;
    }

    final MatchingHandler handler = comment.getUserData(CompiledPattern.HANDLER_KEY);
    if (handler instanceof SubstitutionHandler substitutionHandler) {
      final IElementType tokenType = other.getTokenType();
      final int length = other.getTextLength();
      final int start = tokenType == JavaDocTokenType.DOC_COMMENT_START ? 3 : 2;
      final int end = tokenType == JavaTokenType.END_OF_LINE_COMMENT || length < 4 ? length : length - 2;
      final RegExpPredicate predicate = substitutionHandler.findPredicate(RegExpPredicate.class);
      if (predicate != null) {
        predicate.setNodeTextGenerator(e -> JavaMatchUtil.getCommentText((PsiComment)e).trim());
        myMatchingVisitor.setResult(substitutionHandler.handle(other, myMatchingVisitor.getMatchContext()));
      }
      else {
        myMatchingVisitor.setResult(substitutionHandler.handle(other, start, end, myMatchingVisitor.getMatchContext()));
      }
    }
    else if (handler instanceof LiteralWithSubstitutionHandler) {
      if (comment instanceof PsiDocComment) {
        myMatchingVisitor.setResult(handler.match(comment, other, myMatchingVisitor.getMatchContext()));
      } else {
        final LiteralWithSubstitutionHandler lwsHandler = (LiteralWithSubstitutionHandler) handler;
        int offset = other.getTokenType() == JavaDocTokenType.DOC_COMMENT_START ? 3 : 2;
        String commentText = other.getText();
        while (commentText.length() > offset && commentText.charAt(offset) <= ' ') {
          offset++;
        }
        myMatchingVisitor.setResult(lwsHandler.match(other, JavaMatchUtil.getCommentText(other).trim(), offset, myMatchingVisitor.getMatchContext()));
      }
    }
    else if (handler != null) {
      myMatchingVisitor.setResult(handler.match(comment, other, myMatchingVisitor.getMatchContext()));
    }
    else {
      myMatchingVisitor.setResult(myMatchingVisitor.matchText(MatchUtil.normalize(JavaMatchUtil.getCommentText(comment)),
                                                              MatchUtil.normalize(JavaMatchUtil.getCommentText(other))));
    }
  }

  @Override
  public final void visitModifierList(@NotNull PsiModifierList list) {
    final PsiModifierList other = (PsiModifierList)myMatchingVisitor.getElement();

    for (@PsiModifier.ModifierConstant String modifier : MODIFIERS) {
      if (!myMatchingVisitor.setResult(!list.hasModifierProperty(modifier) || other.hasModifierProperty(modifier))) {
        return;
      }
    }

    final PsiAnnotation[] annotations = list.getAnnotations();
    if (annotations.length > 0) {
      final Set<PsiAnnotation> annotationSet = ContainerUtil.newHashSet(annotations);

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
                if (annotationValueMatchesModifierList(other, v)) {
                  matchedOne = true;
                  break;
                }
              }

              if (!myMatchingVisitor.setResult(matchedOne)) {
                return;
              }
            }
            else {
              if (!myMatchingVisitor.setResult(annotationValueMatchesModifierList(other, value))) {
                return;
              }
            }
          }

          annotationSet.remove(annotation);
        }
      }

      if (!annotationSet.isEmpty()) {
        final PsiAnnotation[] otherAnnotations = other.getAnnotations();
        final List<PsiElement> unmatchedElements = new SmartList<>(otherAnnotations);
        myMatchingVisitor.getMatchContext().pushMatchedElementsListener(elements -> unmatchedElements.removeAll(elements));
        try {
          myMatchingVisitor.setResult(myMatchingVisitor.matchInAnyOrder(annotationSet.toArray(PsiAnnotation.EMPTY_ARRAY), otherAnnotations));
          other.putUserData(GlobalMatchingVisitor.UNMATCHED_ELEMENTS_KEY, unmatchedElements);
        }
        finally {
          myMatchingVisitor.getMatchContext().popMatchedElementsListener();
        }
      }
    }
  }

  private static boolean annotationValueMatchesModifierList(PsiModifierList list, PsiAnnotationMemberValue value) {
    @PsiModifier.ModifierConstant final String name = StringUtil.unquoteString(value.getText());
    if (MatchOptions.INSTANCE_MODIFIER_NAME.equals(name)) {
      return !list.hasModifierProperty(PsiModifier.STATIC) && !list.hasModifierProperty(PsiModifier.ABSTRACT) &&
             list.getParent() instanceof PsiMember;
    }
    return list.hasModifierProperty(name) && (!PsiModifier.PACKAGE_LOCAL.equals(name) || list.getParent() instanceof PsiMember);
  }

  @Override
  public void visitDocTag(@NotNull PsiDocTag tag) {
    final PsiDocTag other = (PsiDocTag)myMatchingVisitor.getElement();
    final CompiledPattern pattern = myMatchingVisitor.getMatchContext().getPattern();
    final boolean isTypedVar = pattern.isTypedVar(tag.getNameElement());

    if (!isTypedVar && !myMatchingVisitor.setResult(tag.getName().equals(other.getName()))) return;

    PsiElement psiDocTagValue = tag.getValueElement();
    boolean isTypedValue = false;

    if (psiDocTagValue != null) {
      final PsiElement[] children = psiDocTagValue.getChildren();
      if (children.length == 1) {
        psiDocTagValue = children[0];
      }
      isTypedValue = pattern.isTypedVar(psiDocTagValue);

      if (isTypedValue) {
        if (other.getValueElement() != null) {
          if (!myMatchingVisitor.setResult(myMatchingVisitor.handleTypedElement(psiDocTagValue, other.getValueElement()))) return;
        }
        else {
          if (!myMatchingVisitor.setResult(myMatchingVisitor.allowsAbsenceOfMatch(psiDocTagValue))) return;
        }
      }
    }

    if (!isTypedValue && !myMatchingVisitor.setResult(myMatchingVisitor.matchInAnyOrder(new DocValuesIterator(tag.getFirstChild()),
                                                                                        new DocValuesIterator(other.getFirstChild())))) {
      return;
    }

    if (isTypedVar) {
      myMatchingVisitor.setResult(myMatchingVisitor.handleTypedElement(tag.getNameElement(), other.getNameElement()));
    }
  }

  @Override
  public void visitDocComment(@NotNull PsiDocComment comment) {
    final PsiDocComment other = myMatchingVisitor.getElement(PsiDocComment.class);
    if (other == null) return;

    final PsiDocTag[] tags = comment.getTags();
    if (tags.length > 0 && !myMatchingVisitor.setResult(myMatchingVisitor.matchInAnyOrder(tags, other.getTags()))) return;
    visitComment(comment);
  }

  @Override
  public void visitElement(@NotNull PsiElement element) {
    myMatchingVisitor.setResult(myMatchingVisitor.matchText(element, myMatchingVisitor.getElement()));
  }

  @Override
  public void visitArrayInitializerExpression(@NotNull PsiArrayInitializerExpression expression) {
    final PsiArrayInitializerExpression other = getExpression(PsiArrayInitializerExpression.class, expression);
    if (other == null) return;
    myMatchingVisitor.setResult(myMatchingVisitor.matchSequentially(expression.getInitializers(), other.getInitializers()));
  }

  @Override
  public void visitClassInitializer(@NotNull PsiClassInitializer initializer) {
    final PsiClassInitializer other = (PsiClassInitializer)myMatchingVisitor.getElement();
    myMatchingVisitor.setResult(myMatchingVisitor.match(initializer.getModifierList(), other.getModifierList()) &&
                                myMatchingVisitor.matchSons(initializer.getBody(), other.getBody()));
  }

  @Override
  public void visitCodeBlock(@NotNull PsiCodeBlock block) {
    myMatchingVisitor.setResult(myMatchingVisitor.matchSons(block, myMatchingVisitor.getElement()));
  }

  @Override
  public void visitJavaToken(@NotNull PsiJavaToken token) {
    final PsiElement other = myMatchingVisitor.getElement();

    myMatchingVisitor.setResult((!(other instanceof PsiJavaToken) || token.getTokenType() == ((PsiJavaToken)other).getTokenType())
                                && (myMatchingVisitor.getMatchContext().getPattern().isTypedVar(token)
                                    ? myMatchingVisitor.handleTypedElement(token, other)
                                    : myMatchingVisitor.matchText(token, other)));
  }

  @Override
  public void visitAnnotation(@NotNull PsiAnnotation annotation) {
    final PsiAnnotation other = (PsiAnnotation)myMatchingVisitor.getElement();
    if (!myMatchingVisitor.setResult(myMatchingVisitor.match(annotation.getNameReferenceElement(), other.getNameReferenceElement()))) return;
    final PsiNameValuePair[] attributes = annotation.getParameterList().getAttributes();
    if (attributes.length == 0) {
      return;
    }
    final PsiNameValuePair[] otherAttributes = other.getParameterList().getAttributes();
    final List<PsiElement> unmatchedElements = new SmartList<>(otherAttributes);
    final MatchContext context = myMatchingVisitor.getMatchContext();
    context.pushMatchedElementsListener(elements -> unmatchedElements.removeAll(elements));
    try {
      if (myMatchingVisitor.setResult(myMatchingVisitor.matchInAnyOrder(attributes, otherAttributes))) {
        other.putUserData(GlobalMatchingVisitor.UNMATCHED_ELEMENTS_KEY, unmatchedElements);
      }
    } finally {
      context.popMatchedElementsListener();
    }
  }

  @Override
  public void visitNameValuePair(@NotNull PsiNameValuePair pair) {
    final PsiNameValuePair other = (PsiNameValuePair)myMatchingVisitor.getElement();

    final MatchContext context = myMatchingVisitor.getMatchContext();
    final PsiIdentifier nameIdentifier = pair.getNameIdentifier();
    final boolean isTypedVar = context.getPattern().isTypedVar(nameIdentifier);
    if (nameIdentifier != null) context.pushResult();
    final PsiIdentifier otherIdentifier = other.getNameIdentifier();
    try {
      final PsiAnnotationMemberValue value1 = pair.getValue();
      final PsiAnnotationMemberValue value2 = other.getValue();
      if (myMatchingVisitor.setResult(myMatchingVisitor.matchSequentially(getElementToMatch(value1), getElementToMatch(value2)) ||
                                      value1 instanceof PsiReferenceExpression && myMatchingVisitor.match(value1, value2))) {
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

  private static PsiElement getElementToMatch(PsiAnnotationMemberValue value) {
    if (value instanceof PsiArrayInitializerMemberValue arrayInitializer) {
      PsiAnnotationMemberValue[] initializers = arrayInitializer.getInitializers();
      if (initializers.length > 0) {
        return initializers[0];
      }
    }
    return value;
  }

  private boolean checkHierarchy(PsiMember element, PsiMember patternElement) {
    final MatchingHandler handler = myMatchingVisitor.getMatchContext().getPattern().getHandler(patternElement);
    if (handler instanceof SubstitutionHandler substitutionHandler) {
      if (!substitutionHandler.isSubtype()) {
        if (substitutionHandler.isStrictSubtype()) {
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
  public void visitField(@NotNull PsiField field) {
    final PsiField other = myMatchingVisitor.getElement(PsiField.class);
    if (other == null) return;
    final PsiDocComment comment = field.getDocComment();
    if (comment != null && !myMatchingVisitor.setResult(myMatchingVisitor.match(comment, other.getDocComment()))) return;
    if (!myMatchingVisitor.setResult(checkHierarchy(other, field))) return;
    super.visitField(field);
  }

  @Override
  public void visitEnumConstant(@NotNull PsiEnumConstant enumConstant) {
    final PsiEnumConstant other = myMatchingVisitor.getElement(PsiEnumConstant.class);
    if (other == null) return;
    final PsiExpressionList argumentList = enumConstant.getArgumentList();
    if (argumentList != null && !myMatchingVisitor.setResult(myMatchingVisitor.matchSons(argumentList, other.getArgumentList()))) return;
    final PsiEnumConstantInitializer enumConstantInitializer = enumConstant.getInitializingClass();
    if (enumConstantInitializer != null &&
        !myMatchingVisitor.setResult(myMatchingVisitor.match(enumConstantInitializer, other.getInitializingClass()))) return;
    super.visitEnumConstant(enumConstant);
  }

  @Override
  public void visitAnonymousClass(@NotNull PsiAnonymousClass clazz) {
    final PsiAnonymousClass other = myMatchingVisitor.getElement(PsiAnonymousClass.class);
    if (other == null) return;
    final PsiElement classReference = clazz.getBaseClassReference();
    final boolean isTypedVar = myMatchingVisitor.getMatchContext().getPattern().isTypedVar(classReference);

    if (myMatchingVisitor.setResult((isTypedVar || myMatchingVisitor.match(clazz.getBaseClassReference(), other.getBaseClassReference())) &&
                                    myMatchingVisitor.matchSons(clazz.getArgumentList(), other.getArgumentList()) &&
                                    matchClasses(clazz, other)) && isTypedVar) {
      myMatchingVisitor.setResult(classReference instanceof LightElement || matchType(classReference, other.getBaseClassReference()));
    }
  }

  @Override
  public void visitLambdaExpression(@NotNull PsiLambdaExpression expression) {
    final PsiLambdaExpression other = getExpression(PsiLambdaExpression.class, expression);
    if (other == null) return;
    final PsiParameterList parameterList1 = expression.getParameterList();
    if (!myMatchingVisitor.setResult(
      parameterList1.isEmpty() || myMatchingVisitor.matchSons(parameterList1, other.getParameterList()))) return;
    final PsiElement body1 = getElementToMatch(expression.getBody());
    if (body1 == null) {
      return;
    }
    final PsiElement body2 = getElementToMatch(other.getBody());
    if (body1 instanceof PsiExpression && (body2 == null || body2 instanceof PsiStatement || body2 instanceof PsiComment)) {
      final PsiElement parent = body1.getParent();
      myMatchingVisitor.setResult(parent instanceof PsiStatement
                                  ? myMatchingVisitor.matchSequentially(parent, body2)
                                  : myMatchingVisitor.matchSequentially(body1, (body2 == null) ? other.getBody() : body2.getParent()));
    }
    else {
      myMatchingVisitor.setResult(myMatchingVisitor.matchSequentially(body1, body2));
    }
  }

  private PsiElement getElementToMatch(PsiElement element) {
    if (myMatchingVisitor.getMatchContext().getOptions().isLooseMatching()) {
      if (element instanceof PsiCodeBlock) {
        final List<PsiElement> list = PsiTreeUtil.getChildrenOfAnyType(element, PsiStatement.class, PsiComment.class);
        if (list.isEmpty()) return null;
        element = list.get(0);
        if (list.size() > 1) return element;
      }
      if (element instanceof PsiExpressionStatement expressionStatement) {
        element = expressionStatement.getExpression();
      }
      if (element instanceof PsiReturnStatement returnStatement) {
        element = returnStatement.getReturnValue();
      }
    }
    return element;
  }

  private boolean matchInAnyOrder(PsiReferenceList patternElements, PsiReferenceList matchElements) {
    if (patternElements == null) return myMatchingVisitor.isLeftLooseMatching() || matchElements == null;

    return myMatchingVisitor.matchInAnyOrder(
      patternElements.getReferenceElements(),
      (matchElements != null) ? matchElements.getReferenceElements() : PsiElement.EMPTY_ARRAY
    );
  }

  @Override
  public void visitRecordHeader(@NotNull PsiRecordHeader recordHeader) {
    final PsiRecordHeader other = myMatchingVisitor.getElement(PsiRecordHeader.class);
    myMatchingVisitor.setResult(myMatchingVisitor.matchSequentially(recordHeader.getRecordComponents(), other.getRecordComponents()));
  }

  private boolean matchClasses(PsiClass patternClass, PsiClass matchClass) {
    final PsiClass saveClazz = myClazz;
    myClazz = matchClass;
    final MatchContext context = myMatchingVisitor.getMatchContext();
    final JavaCompiledPattern javaPattern = (JavaCompiledPattern)context.getPattern();

    final Set<PsiElement> matchedElements = new HashSet<>();
    context.pushMatchedElementsListener(elements -> matchedElements.addAll(elements));
    try {
      final boolean templateIsInterface = patternClass.isInterface();
      if (templateIsInterface && !matchClass.isInterface()) return false;
      if (templateIsInterface && patternClass.isAnnotationType() && !matchClass.isAnnotationType()) return false;
      if (patternClass.isEnum() && !matchClass.isEnum()) return false;
      if (patternClass instanceof PsiTypeParameter != matchClass instanceof PsiTypeParameter) return false;
      if (patternClass.isRecord() && !matchClass.isRecord()) return false;

      if (!myMatchingVisitor.match(patternClass.getRecordHeader(), matchClass.getRecordHeader())) {
        return false;
      }
      if (!matchInAnyOrder(patternClass.getExtendsList(), matchClass.getExtendsList())) {
        return false;
      }

      // check if implements is in extended classes implements
      final PsiReferenceList implementsList = patternClass.getImplementsList();
      if (implementsList != null) {
        if (implementsList.getFirstChild() != null && matchClass.isInterface()) return false;
        final List<PsiJavaCodeReferenceElement> elements = new SmartList<>();
        for (PsiJavaCodeReferenceElement element : implementsList.getReferenceElements()) {
          final MatchingHandler handler = javaPattern.getHandler(element);
          if (handler instanceof SubstitutionHandler substitutionHandler &&
              (substitutionHandler.isSubtype() || substitutionHandler.isStrictSubtype())) {
            if (!matchWithinHierarchy(element, matchClass, substitutionHandler)) {
              return false;
            }
            continue;
          }
          elements.add(element);
        }
        if (!elements.isEmpty()) {
          final PsiReferenceList implementsList2 = matchClass.getImplementsList();
          final PsiElement[] matchElements = (implementsList2 == null) ? PsiElement.EMPTY_ARRAY : implementsList2.getReferenceElements();
          if (!myMatchingVisitor.matchInAnyOrder(elements.toArray(PsiElement.EMPTY_ARRAY), matchElements)) {
            return false;
          }
        }
      }

      final PsiField[] fields = PsiTreeUtil.getChildrenOfType(patternClass, PsiField.class);
      if (fields != null) {
        final PsiField[] fields2 = javaPattern.isRequestsSuperFields() ?
                                   matchClass.getAllFields() :
                                   matchClass.getFields();

        if (!myMatchingVisitor.matchInAnyOrder(fields, fields2)) {
          return false;
        }
      }

      final PsiMethod[] methods = PsiTreeUtil.getChildrenOfType(patternClass, PsiMethod.class);
      if (methods != null) {
        final PsiMethod[] methods2 = javaPattern.isRequestsSuperMethods() ?
                                     matchClass.getAllMethods() :
                                     matchClass.getMethods();

        if (!myMatchingVisitor.matchInAnyOrder(methods, methods2)) {
          return false;
        }
      }

      final PsiClass[] nestedClasses = PsiTreeUtil.getChildrenOfType(patternClass, PsiClass.class);
      if (nestedClasses != null) {
        final PsiClass[] nestedClasses2 = javaPattern.isRequestsSuperInners() ?
                                          matchClass.getAllInnerClasses() :
                                          matchClass.getInnerClasses();

        if (!myMatchingVisitor.matchInAnyOrder(nestedClasses, nestedClasses2)) {
          return false;
        }
      }

      final PsiClassInitializer[] initializers = patternClass.getInitializers();
      if (initializers.length > 0) {
        final PsiClassInitializer[] initializers2 = matchClass.getInitializers();

        if (!myMatchingVisitor.matchInAnyOrder(initializers, initializers2)) {
          return false;
        }
      }

      final List<PsiElement> unmatchedElements = new SmartList<>(PsiTreeUtil.getChildrenOfTypeAsList(matchClass, PsiMember.class));
      unmatchedElements.removeAll(matchedElements);
      MatchingHandler unmatchedSubstitutionHandler = null;
      for (PsiElement element = patternClass.getLBrace(); element != null; element = element.getNextSibling()) {
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
        matchClass.putUserData(GlobalMatchingVisitor.UNMATCHED_ELEMENTS_KEY, unmatchedElements);
      }

      return true;
    }
    finally {
      myClazz = saveClazz;
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
  public void visitArrayAccessExpression(@NotNull PsiArrayAccessExpression expression) {
    final PsiArrayAccessExpression other = getExpression(PsiArrayAccessExpression.class, expression);
    if (other != null) {
      myMatchingVisitor.setResult(myMatchingVisitor.match(expression.getArrayExpression(), other.getArrayExpression()) &&
                                  myMatchingVisitor.match(expression.getIndexExpression(), other.getIndexExpression()));
    }
  }

  @Override
  public void visitMethodReferenceExpression(@NotNull PsiMethodReferenceExpression expression) {
    if (getExpression(PsiMethodReferenceExpression.class, expression) == null) return;
    super.visitMethodReferenceExpression(expression);
  }








  @Override
  public void visitReferenceExpression(@NotNull PsiReferenceExpression reference) {
    final MatchContext context = myMatchingVisitor.getMatchContext();
    final CompiledPattern pattern = context.getPattern();
    final PsiElement referenceNameElement = reference.getReferenceNameElement();
    assert referenceNameElement != null;
    MatchingHandler _handler = pattern.getHandlerSimple(referenceNameElement);
    boolean special = false;
    if (_handler == null) {
      _handler = pattern.getHandlerSimple(reference);
      special = reference.resolve() != null;
    }

    final PsiElement other = myMatchingVisitor.getElement();
    final PsiElement unwrapped = unwrap(other, context);
    final PsiExpression qualifier = reference.getQualifierExpression();
    if (_handler instanceof SubstitutionHandler handler && (qualifier == null || special)) {
      if (other instanceof PsiReferenceExpression && other.getParent() instanceof PsiMethodCallExpression) {
        myMatchingVisitor.setResult(false);
        return;
      }
      if (handler.isSubtype() || handler.isStrictSubtype()) {
        if (myMatchingVisitor.setResult(matchWithinHierarchy(reference, unwrapped, handler))) {
          handler.addResult(other, myMatchingVisitor.getMatchContext());
        }
      }
      else if (myMatchingVisitor.setResult(handler.validate(unwrapped, context))) {
        handler.addResult(other, context);
      }
      return;
    }

    final boolean multiMatch = unwrapped != null && reference.getContainingFile() == unwrapped.getContainingFile();
    if (!(unwrapped instanceof PsiReferenceExpression reference2)) {
      // when the same variable is used multiple times in a pattern, we need to check if they are the same,
      // but sometimes they are not normally comparable. In this case we fall back to a text comparison.
      // For example in the pattern `boolean equals(Object $x$) { return super.equals($x$); }`
      // the PsiReferenceExpression argument will be compared to the PsiIdentifier of the parameter.
      myMatchingVisitor.setResult(multiMatch && myMatchingVisitor.matchText(reference, unwrapped));
      return;
    }

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
      myMatchingVisitor.setResult(myMatchingVisitor.matchText(referenceNameElement, reference2.getReferenceNameElement()));
      return;
    }

    // handle field selection
    if (myMatchingVisitor.setResult(!(other.getParent() instanceof PsiMethodCallExpression) && qualifier != null)) {
      final PsiElement referenceNameElement2 = reference2.getReferenceNameElement();

      if (pattern.isTypedVar(referenceNameElement)) {
        if (!myMatchingVisitor.setResult(myMatchingVisitor.handleTypedElement(referenceNameElement, referenceNameElement2))) return;
      }
      else {
        if (!myMatchingVisitor.setResult(myMatchingVisitor.matchText(referenceNameElement, referenceNameElement2))) return;
      }

      if (!myMatchingVisitor.setResult(qualifier instanceof PsiThisExpression && qualifier2 == null ||
                                       myMatchingVisitor.matchOptionally(qualifier, qualifier2))) return;
      if (qualifier2 == null) myMatchingVisitor.setResult(matchImplicitQualifier(qualifier, unwrapped, context));
    }
  }

  /** Removes parentheses from the element if it is a parenthesized expression. */
  private static PsiElement unwrap(PsiElement element, @NotNull MatchContext context) {
    return context.getOptions().isLooseMatching() && element instanceof PsiExpression
           ? PsiUtil.skipParenthesizedExprDown((PsiExpression)element)
           : element;
  }

  private static int getArrayDimensions(PsiElement element) {
    if (element == null) {
      return 0;
    }
    final PsiElement parent = element.getParent();
    if (parent instanceof PsiVariable variable) {
      final PsiType type = variable.getType();
      return type.getArrayDimensions();
    }
    else if (parent instanceof PsiMethod method) {
      final PsiType type = method.getReturnType();
      return (type == null) ? 0 : type.getArrayDimensions();
    }
    else if (element instanceof PsiTypeElement typeElement) {
      final PsiType type = typeElement.getType();
      return type.getArrayDimensions();
    }
    return 0;
  }

  @NotNull
  private static PsiTypeElement getInnermostComponentTypeElement(@NotNull PsiTypeElement typeElement) {
    PsiElement child = typeElement.getFirstChild();
    while (child instanceof PsiTypeElement) {
      typeElement = (PsiTypeElement)child;
      child = typeElement.getFirstChild();
    }
    return typeElement;
  }

  @Contract("!null->!null; null->null")
  private static PsiElement getInnermostComponent(PsiElement element) {
    if (!(element instanceof PsiTypeElement typeElement)) {
      return element;
    }
    if (typeElement.getType() instanceof PsiDisjunctionType) {
      // getInnermostComponentReferenceElement() doesn't make sense for disjunction type
      return typeElement;
    }
    if (typeElement.isInferredType()) {
      // replace inferred type with explicit type if possible
      final PsiType type = typeElement.getType();
      if (type == PsiTypes.nullType() || type instanceof PsiLambdaParameterType || type instanceof PsiLambdaExpressionType) {
        return typeElement;
      }
      final String canonicalText = type.getCanonicalText();
      typeElement = JavaPsiFacade.getElementFactory(typeElement.getProject()).createTypeElementFromText(canonicalText, typeElement);
    }
    final PsiJavaCodeReferenceElement referenceElement = typeElement.getInnermostComponentReferenceElement();
    return (referenceElement != null) ? referenceElement : getInnermostComponentTypeElement(typeElement);
  }

  private static PsiTypeElement[] getTypeParameters(@NotNull PsiJavaCodeReferenceElement referenceElement, Boolean replaceDiamondWithExplicitTypes) {
    final PsiReferenceParameterList referenceElementParameterList = referenceElement.getParameterList();
    if (referenceElementParameterList == null) {
      return null;
    }
    final PsiTypeElement[] typeParameterElements = referenceElementParameterList.getTypeParameterElements();
    if (typeParameterElements.length != 1 || replaceDiamondWithExplicitTypes == Boolean.FALSE) {
      return typeParameterElements;
    }
    final PsiType type = typeParameterElements[0].getType();
    if (!(type instanceof PsiDiamondType diamondType)) {
      return typeParameterElements;
    }
    if (replaceDiamondWithExplicitTypes == null) {
      return null;
    }
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

  private Boolean shouldReplaceDiamondWithExplicitTypes(@NotNull PsiElement element) {
    if (!(element instanceof PsiJavaCodeReferenceElement javaCodeReferenceElement)) {
      return Boolean.TRUE;
    }
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
    if (handler instanceof SubstitutionHandler substitutionHandler && substitutionHandler.getMinOccurs() > 0) {
      return null;
    }
    return Boolean.valueOf(!(typeElement.getType() instanceof PsiDiamondType));
  }

  private boolean matchType(@NotNull PsiElement patternType, PsiElement matchedType) {
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
      if (type1 instanceof PsiWildcardType wildcardType1 && type2 instanceof PsiWildcardType wildcardType2) {
        if (wildcardType1.equals(wildcardType2)) return true;
        if (wildcardType1.isExtends() && (wildcardType2.isExtends() || !wildcardType2.isBounded())) {
          if (wildcardType2.isExtends()) {
            return myMatchingVisitor.match(patternElement.getLastChild(), matchedElement.getLastChild());
          }
          else {
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
        regExpPredicate = handler.findPredicate(RegExpPredicate.class);

        if (regExpPredicate != null) {
          regExpPredicate.setNodeTextGenerator(
            element -> StructuralSearchUtil.getMeaningfulText(element) + "[]".repeat(matchedArrayDimensions));
        }
        fullTypeResult = true;
      }

      try {
        final boolean result = (handler.isSubtype() || handler.isStrictSubtype())
                               ? matchWithinHierarchy(patternElement, matchedElement, handler)
                               : handler.validate(matchedElement, myMatchingVisitor.getMatchContext());
        if (result) handler.addResult(fullTypeResult ? matchedType : matchedElement, myMatchingVisitor.getMatchContext());
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
  @NotNull
  private static String getText(@NotNull PsiElement element) {
    String result;
    if (element instanceof PsiClass) {
      result = ((PsiClass)element).getQualifiedName();
      if (result == null) result = element.getText();
    }
    else if (element instanceof PsiJavaCodeReferenceElement) {
      result = ((PsiJavaCodeReferenceElement)element).getCanonicalText();
    }
    else if (element instanceof PsiTypeElement typeElement) {
      result = typeElement.isInferredType() ? typeElement.getText() : typeElement.getType().getCanonicalText();
    }
    else {
      result = element.getText();
    }
    final int index = result.indexOf('<');
    return index == -1 ? result : result.substring(0, index);
  }

  private boolean matchWithinHierarchy(@Nullable PsiElement patternElement, PsiElement matchElement, SubstitutionHandler handler) {
    boolean includeInterfaces = true;
    boolean includeClasses = true;
    if (patternElement != null) {
      patternElement = StructuralSearchUtil.getParentIfIdentifier(patternElement);
      final PsiElement patternParent = patternElement.getParent();

      if (patternParent instanceof PsiReferenceList && patternParent.getParent() instanceof PsiClass psiClass) {
        if (patternParent == psiClass.getExtendsList()) {
          includeInterfaces = psiClass.isInterface();
        }
        else if (patternParent == psiClass.getImplementsList()) {
          includeClasses = false;
        }
      }
    }

    final NodeIterator nodes = new HierarchyNodeIterator(matchElement, includeClasses, includeInterfaces);
    if (!includeClasses) {
      nodes.advance();
    }
    if (handler.isStrictSubtype()) {
      nodes.advance();
    }

    final boolean negated = handler.getPredicate() instanceof NotPredicate;
    while (nodes.hasNext() && negated == handler.validate(nodes.current(), myMatchingVisitor.getMatchContext())) {
      nodes.advance();
    }
    return negated != nodes.hasNext();
  }

  @Override
  public void visitConditionalExpression(@NotNull PsiConditionalExpression expression) {
    final PsiConditionalExpression other = getExpression(PsiConditionalExpression.class, expression);
    if (other == null) return;
    myMatchingVisitor.setResult(myMatchingVisitor.match(expression.getCondition(), other.getCondition()) &&
                                myMatchingVisitor.matchSons(expression, other));
  }

  @Override
  public void visitPolyadicExpression(@NotNull PsiPolyadicExpression expression) {
    final PsiPolyadicExpression other = getExpression(PsiPolyadicExpression.class, expression);
    if (other == null) return;
    if (myMatchingVisitor.setResult(expression.getOperationTokenType().equals(other.getOperationTokenType()))) {
      myMatchingVisitor.setResult(myMatchingVisitor.matchSequentially(expression.getOperands(), other.getOperands()));
    }
  }

  @Override
  public void visitVariable(@NotNull PsiVariable var) {
    final PsiVariable other = (PsiVariable)myMatchingVisitor.getElement();

    final MatchContext context = myMatchingVisitor.getMatchContext();
    final PsiIdentifier nameIdentifier = var.getNameIdentifier();
    final boolean isTypedVar = context.getPattern().isTypedVar(nameIdentifier);
    context.pushResult();
    try {
      if (!myMatchingVisitor.setResult((isTypedVar || myMatchingVisitor.matchText(nameIdentifier, other.getNameIdentifier())) &&
                                       myMatchingVisitor.match(var.getModifierList(), other.getModifierList()))) return;
      final PsiTypeElement typeElement1 = var.getTypeElement();
      if (typeElement1 != null) {
        PsiTypeElement typeElement2 = other.getTypeElement();
        if (typeElement2 == null) { // e.g., lambda parameter without explicit type
          typeElement2 = JavaPsiFacade.getElementFactory(other.getProject()).createTypeElement(other.getType());
          final MatchingHandler matchingHandler = context.getPattern().getHandler(typeElement1);
          if (matchingHandler instanceof SubstitutionHandler) {
            if (!myMatchingVisitor.setResult(myMatchingVisitor.matchOptionally(typeElement1, null)) ||
                !myMatchingVisitor.setResult(((SubstitutionHandler)matchingHandler).validate(typeElement2, context))) {
              return;
            }
          }
          else if (!myMatchingVisitor.setResult(myMatchingVisitor.match(typeElement1, typeElement2))) return;
        }
        else if (!myMatchingVisitor.setResult(myMatchingVisitor.matchOptionally(typeElement1, typeElement2))) return;
      }

      // Check initializer
      final PsiExpression initializer = var.getInitializer();
      final PsiExpression var2Initializer = other.getInitializer();
      myMatchingVisitor.setResult(myMatchingVisitor.matchOptionally(initializer, var2Initializer));
    }
    finally {
      final PsiElement identifier = other instanceof PsiReceiverParameter
                                    ? ((PsiReceiverParameter)other).getIdentifier()
                                    : other.getNameIdentifier();
      final String name;
      if (identifier == null && (name = other.getName()) != null) {
        // when matching a stub or compiled code
        final PsiIdentifier fakeIdentifier = JavaPsiFacade.getElementFactory(other.getProject()).createIdentifier(name);
        myMatchingVisitor.scopeMatch(nameIdentifier, isTypedVar, fakeIdentifier);
      }
      else {
        myMatchingVisitor.scopeMatch(nameIdentifier, isTypedVar, identifier);
      }
    }
  }

  private void matchArrayOrArguments(@NotNull PsiNewExpression patternExpression, @NotNull PsiNewExpression matchExpression) {
    final PsiType type1 = patternExpression.getType();
    final PsiType type2 = matchExpression.getType();
    if (!myMatchingVisitor.setResult(type1 != null && type2 != null && type1.getArrayDimensions() == type2.getArrayDimensions())) return;
    final PsiArrayInitializerExpression initializer1 = patternExpression.getArrayInitializer();
    final PsiArrayInitializerExpression initializer2 = matchExpression.getArrayInitializer();
    if (initializer1 != null) {
      if (!myMatchingVisitor.setResult(myMatchingVisitor.matchSons(initializer1, initializer2))) return;
    }
    else if (initializer2 != null) {
      myMatchingVisitor.setResult(areZeroLiterals(patternExpression.getArrayDimensions()) && initializer2.getInitializers().length == 0);
      return;
    }

    final PsiExpression[] dimensions1 = patternExpression.getArrayDimensions();
    final PsiExpression[] dimensions2 = matchExpression.getArrayDimensions();
    if (!myMatchingVisitor.setResult(dimensions1.length == dimensions2.length)) return;
    if (dimensions1.length != 0) {
      for (int i = 0; i < dimensions1.length; ++i) {
        if (!myMatchingVisitor.setResult(myMatchingVisitor.match(dimensions1[i], dimensions2[i]))) return;
      }
    }
    else {
      myMatchingVisitor.setResult(myMatchingVisitor.matchSons(patternExpression.getArgumentList(), matchExpression.getArgumentList()) &&
                                  myMatchingVisitor.setResult(matchTypeParameters(patternExpression, matchExpression)));
    }
  }

  private static boolean areZeroLiterals(PsiExpression @NotNull [] expressions) {
    for (PsiExpression expression : expressions) {
      if (!(expression instanceof PsiLiteralExpression) || !expression.getText().equals("0")) return false;
    }
    return true;
  }

  private boolean matchImplicitQualifier(@NotNull PsiExpression qualifier, @NotNull PsiElement reference, @NotNull MatchContext context) {
    final PsiElement target = reference instanceof PsiMethodCallExpression
                              ? ((PsiMethodCallExpression)reference).resolveMethod()
                              : ((PsiReference)reference).resolve();
    if (target instanceof PsiMember && qualifier instanceof PsiThisExpression) {
      return !((PsiMember)target).hasModifierProperty(PsiModifier.STATIC) &&
             (target instanceof PsiField || target instanceof PsiMethod);
    }
    final SubstitutionHandler handler = ObjectUtils.tryCast(context.getPattern().getHandler(qualifier), SubstitutionHandler.class);
    if (handler == null) {
      return false;
    }
    if (target instanceof PsiModifierListOwner && ((PsiModifierListOwner)target).hasModifierProperty(PsiModifier.STATIC)) {
      final PsiClass containingClass = target instanceof PsiMember
                                       ? PsiTreeUtil.getParentOfType(target, PsiClass.class)
                                       : ((PsiMember)target).getContainingClass();
      return handler.isSubtype() || handler.isStrictSubtype()
             ? matchWithinHierarchy(null, containingClass, handler)
             : handler.validate(containingClass, context);
    }
    else {
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(reference.getProject());
      final PsiExpression implicitReference = factory.createExpressionFromText("this", reference);
      return handler.validate(implicitReference, context);
    }
  }

  @Override
  public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
    final PsiMethodCallExpression other = getExpression(PsiMethodCallExpression.class, expression);
    if (other == null) return;
    final PsiReferenceExpression ref1 = expression.getMethodExpression();
    final PsiReferenceExpression ref2 = other.getMethodExpression();

    final PsiElement patternMethodName = ref1.getReferenceNameElement();
    final MatchContext context = myMatchingVisitor.getMatchContext();
    final boolean isTypedVar = context.getPattern().isTypedVar(patternMethodName);
    if (!isTypedVar && !myMatchingVisitor.setResult(myMatchingVisitor.matchText(patternMethodName, ref2.getReferenceNameElement()))) {
      return;
    }

    final PsiExpression patternQualifier = ref1.getQualifierExpression();
    final PsiExpression matchedQualifier = ref2.getQualifierExpression();
    if (!myMatchingVisitor.setResult(patternQualifier instanceof PsiThisExpression && matchedQualifier == null ||
                                     myMatchingVisitor.matchOptionally(patternQualifier, matchedQualifier))) return;
    if (patternQualifier != null && matchedQualifier == null) {
      if (!myMatchingVisitor.setResult(matchImplicitQualifier(patternQualifier, other, context))) return;
    }

    if (!myMatchingVisitor.setResult(myMatchingVisitor.matchSons(expression.getArgumentList(), other.getArgumentList()))) return;
    if (!myMatchingVisitor.setResult(matchTypeParameters(expression, other))) return;
    if (isTypedVar) {
      myMatchingVisitor.setResult(myMatchingVisitor.handleTypedElement(patternMethodName, ref2.getReferenceNameElement()));
    }
  }

  private boolean matchTypeParameters(@NotNull PsiCallExpression call1, @NotNull PsiCallExpression call2) {
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
  public void visitExpressionStatement(@NotNull PsiExpressionStatement statement) {
    final PsiElement other = myMatchingVisitor.getElement();
    if (myMatchingVisitor.setResult(other instanceof PsiExpressionStatement)) {
      final PsiExpressionStatement expr2 = (PsiExpressionStatement)other;
      myMatchingVisitor.setResult(myMatchingVisitor.match(statement.getExpression(), expr2.getExpression()));
    }
  }

  @Override
  public void visitLiteralExpression(@NotNull PsiLiteralExpression expression) {
    final PsiLiteralExpression other = getExpression(PsiLiteralExpression.class, expression);
    if (other == null) return;
    final PsiType type1 = expression.getType();
    if (type1 != null && !myMatchingVisitor.setResult(type1.equals(other.getType()))) {
      return;
    }
    final MatchingHandler handler = expression.getUserData(CompiledPattern.HANDLER_KEY);
    if (handler instanceof SubstitutionHandler) {
      int offset = 0;
      int length = other.getTextLength();
      final String text = other.getText();

      if (StringUtil.isQuotedString(text)) {
        length--;
        offset++;
      }
      myMatchingVisitor.setResult(((SubstitutionHandler)handler).handle(other, offset, length, myMatchingVisitor.getMatchContext()));
    }
    else if (handler != null) {
      myMatchingVisitor.setResult(handler.match(expression, other, myMatchingVisitor.getMatchContext()));
    }
    else {
      final Object value1 = expression.getValue();
      final Object value2 = other.getValue();
      if ((value1 instanceof String || value1 instanceof Character) && (value2 instanceof String || value2 instanceof Character)) {
        String patternValue = value1.toString();
        if (!patternValue.isEmpty() && patternValue.equals(patternValue.trim())) {
          myMatchingVisitor.setResult(myMatchingVisitor.matchText(MatchUtil.normalize(patternValue),
                                                                  MatchUtil.normalize(value2.toString())));
        }
        else {
          myMatchingVisitor.setResult(myMatchingVisitor.matchText(patternValue, value2.toString()));
        }
      }
      else if (value1 != null && value2 != null) {
        myMatchingVisitor.setResult(value1.equals(value2));
      }
      else {
        // matches null literals
        myMatchingVisitor.setResult(myMatchingVisitor.matchText(expression, other));
      }
    }
  }

  @Override
  public void visitAssignmentExpression(@NotNull PsiAssignmentExpression expression) {
    final PsiAssignmentExpression other = getExpression(PsiAssignmentExpression.class, expression);
    if (other != null) {
      myMatchingVisitor.setResult(expression.getOperationTokenType().equals(other.getOperationTokenType()) &&
                                  myMatchingVisitor.match(expression.getLExpression(), other.getLExpression()) &&
                                  myMatchingVisitor.match(expression.getRExpression(), other.getRExpression()));
    }
  }

  @Override
  public void visitIfStatement(@NotNull PsiIfStatement statement) {
    final PsiIfStatement other = (PsiIfStatement)myMatchingVisitor.getElement();

    final PsiStatement elseBranch = statement.getElseBranch();
    myMatchingVisitor.setResult(myMatchingVisitor.match(statement.getCondition(), other.getCondition()) &&
                                matchBody(statement.getThenBranch(), other.getThenBranch()) &&
                                (elseBranch == null || matchBody(elseBranch, other.getElseBranch())));
  }

  @Override
  public void visitSwitchStatement(@NotNull PsiSwitchStatement statement) {
    myMatchingVisitor.setResult(matchSwitchBlock(statement));
  }

  @Override
  public void visitSwitchExpression(@NotNull PsiSwitchExpression expression) {
    myMatchingVisitor.setResult(matchSwitchBlock(expression));
  }

  private boolean matchSwitchBlock(@NotNull PsiSwitchBlock switchBlock) {
    final PsiSwitchBlock other = (PsiSwitchBlock)myMatchingVisitor.getElement();

    if (!myMatchingVisitor.match(switchBlock.getExpression(), other.getExpression())) {
      return false;
    }
    final PsiCodeBlock body = switchBlock.getBody();
    final PsiSwitchLabelStatementBase[] cases1 = PsiTreeUtil.getChildrenOfType(body, PsiSwitchLabelStatementBase.class);
    if (cases1 != null) {
      final PsiSwitchLabelStatementBase[] cases2 = PsiTreeUtil.getChildrenOfType(other.getBody(), PsiSwitchLabelStatementBase.class);
      return myMatchingVisitor.matchSequentially(cases1, cases2 != null ? cases2 : PsiElement.EMPTY_ARRAY);
    }
    final List<PsiElement> statements1 = PsiTreeUtil.getChildrenOfAnyType(body, PsiStatement.class, PsiComment.class);
    if (!statements1.isEmpty()) {
      final List<PsiElement> statements2 = PsiTreeUtil.getChildrenOfAnyType(other.getBody(), PsiStatement.class, PsiComment.class);
      return myMatchingVisitor.matchSequentially(statements1.toArray(PsiElement.EMPTY_ARRAY), statements2.toArray(PsiElement.EMPTY_ARRAY));
    }
    return true;
  }

  @Override
  public void visitSwitchLabelStatement(@NotNull PsiSwitchLabelStatement statement) {
    myMatchingVisitor.setResult(matchLabelStatement(statement, (PsiSwitchLabelStatementBase)myMatchingVisitor.getElement()));
  }

  @Override
  public void visitSwitchLabeledRuleStatement(@NotNull PsiSwitchLabeledRuleStatement statement) {
    myMatchingVisitor.setResult(matchLabelStatement(statement, (PsiSwitchLabelStatementBase)myMatchingVisitor.getElement()));
  }

  private boolean matchLabelStatement(@NotNull PsiSwitchLabelStatementBase statement1, @NotNull PsiSwitchLabelStatementBase statement2) {
    final PsiCaseLabelElementList labelElementList1 = statement1.getCaseLabelElementList();
    final PsiCaseLabelElementList labelElementList2 = statement2.getCaseLabelElementList();
    if (statement1.isDefaultCase() && !statement2.isDefaultCase()) {
      return false;
    }
    if (labelElementList1 == null) {
      return true;
    }
    final PsiCaseLabelElement[] caseLabelElements =
      (labelElementList2 == null) ? PsiCaseLabelElement.EMPTY_ARRAY : labelElementList2.getElements();
    if (!myMatchingVisitor.matchInAnyOrder(labelElementList1.getElements(), caseLabelElements)) {
      return false;
    }
    final PsiElement[] body = getBody(statement1);
    return body.length == 0 || myMatchingVisitor.matchSequentially(body, getBody(statement2));
  }

  private static PsiElement @NotNull [] getBody(@NotNull PsiSwitchLabelStatementBase switchLabelStatement) {
    final List<PsiElement> result = new SmartList<>();
    if (switchLabelStatement instanceof PsiSwitchLabeledRuleStatement) {
      final PsiStatement body = ((PsiSwitchLabeledRuleStatement)switchLabelStatement).getBody();
      if (body instanceof PsiBlockStatement) {
        result.addAll(PsiTreeUtil.getChildrenOfAnyType(((PsiBlockStatement)body).getCodeBlock(), PsiStatement.class, PsiComment.class));
      }
      else {
        result.add(body);
      }
    }
    else {
      PsiElement sibling = PsiTreeUtil.getNextSiblingOfType(switchLabelStatement, PsiStatement.class);
      while (sibling != null && !(sibling instanceof PsiSwitchLabelStatement)) {
        if (sibling instanceof PsiStatement || sibling instanceof PsiComment) {
          result.add(sibling);
        }
        sibling = sibling.getNextSibling();
      }
    }
    return result.toArray(PsiElement.EMPTY_ARRAY);
  }

  @Override
  public void visitYieldStatement(@NotNull PsiYieldStatement statement) {
    final PsiYieldStatement other = (PsiYieldStatement)myMatchingVisitor.getElement();
    myMatchingVisitor.setResult(myMatchingVisitor.match(statement.getExpression(), other.getExpression()));
  }

  @Override
  public void visitForStatement(@NotNull PsiForStatement statement) {
    final PsiForStatement other = (PsiForStatement)myMatchingVisitor.getElement();

    final PsiStatement initialization = statement.getInitialization();
    if (!myMatchingVisitor.setResult(initialization == null || initialization instanceof PsiEmptyStatement
                                     ? myMatchingVisitor.isLeftLooseMatching()
                                     : myMatchingVisitor.matchSequentially(getIterator(initialization),
                                                                           getIterator(other.getInitialization())))) {
      return;
    }
    if (!myMatchingVisitor.setResult(myMatchingVisitor.matchOptionally(statement.getCondition(), other.getCondition()))) {
      return;
    }
    final PsiStatement update = statement.getUpdate();
    if (!myMatchingVisitor.setResult(update == null
                                     ? myMatchingVisitor.isLeftLooseMatching()
                                     : myMatchingVisitor.matchSequentially(getIterator(update), getIterator(other.getUpdate())))) {
      return;
    }
    myMatchingVisitor.setResult(matchBody(statement.getBody(), other.getBody()));
  }

  private static NodeIterator getIterator(PsiStatement statement) {
    if (statement instanceof PsiExpressionListStatement expressionListStatement) {
      return new ArrayBackedNodeIterator(expressionListStatement.getExpressionList().getExpressions());
    }
    else if (statement instanceof PsiExpressionStatement expressionStatement) {
      return SingleNodeIterator.create(expressionStatement.getExpression());
    }
    else if (statement instanceof PsiEmptyStatement) {
      return SingleNodeIterator.EMPTY;
    }
    else {
      return SingleNodeIterator.create(statement);
    }
  }

  @Override
  public void visitForeachStatement(@NotNull PsiForeachStatement statement) {
    final PsiForeachStatement other = (PsiForeachStatement)myMatchingVisitor.getElement();

    myMatchingVisitor.setResult(myMatchingVisitor.match(statement.getIterationParameter(), other.getIterationParameter()) &&
                                myMatchingVisitor.match(statement.getIteratedValue(), other.getIteratedValue()) &&
                                matchBody(statement.getBody(), other.getBody()));
  }

  @Override
  public void visitWhileStatement(@NotNull PsiWhileStatement statement) {
    final PsiWhileStatement other = (PsiWhileStatement)myMatchingVisitor.getElement();

    myMatchingVisitor.setResult(myMatchingVisitor.match(statement.getCondition(), other.getCondition()) &&
                                matchBody(statement.getBody(), other.getBody()));
  }

  @Override
  public void visitBlockStatement(@NotNull PsiBlockStatement statement) {
    final PsiElement other = myMatchingVisitor.getElement();
    if (other instanceof PsiCodeBlock) {
      myMatchingVisitor.setResult(!(other.getParent() instanceof PsiBlockStatement) &&
                                  myMatchingVisitor.matchSons(statement.getCodeBlock(), other));
    }
    else {
      myMatchingVisitor.setResult(myMatchingVisitor.matchSons(statement, other));
    }
  }

  @Override
  public void visitDeclarationStatement(@NotNull PsiDeclarationStatement statement) {
    final PsiDeclarationStatement other = (PsiDeclarationStatement)myMatchingVisitor.getElement();
    myMatchingVisitor.setResult(myMatchingVisitor.matchInAnyOrder(statement.getDeclaredElements(), other.getDeclaredElements()));
  }

  @Override
  public void visitDoWhileStatement(@NotNull PsiDoWhileStatement statement) {
    final PsiDoWhileStatement other = (PsiDoWhileStatement)myMatchingVisitor.getElement();

    myMatchingVisitor.setResult(myMatchingVisitor.match(statement.getCondition(), other.getCondition()) &&
                                matchBody(statement.getBody(), other.getBody()));
  }

  @Override
  public void visitReturnStatement(@NotNull PsiReturnStatement statement) {
    final PsiReturnStatement other = (PsiReturnStatement)myMatchingVisitor.getElement();

    myMatchingVisitor.setResult(myMatchingVisitor.matchOptionally(statement.getReturnValue(), other.getReturnValue()));
  }

  @Override
  public void visitPostfixExpression(@NotNull PsiPostfixExpression expression) {
    final PsiPostfixExpression other = getExpression(PsiPostfixExpression.class, expression);
    if (other == null) return;
    myMatchingVisitor.setResult(expression.getOperationTokenType().equals(other.getOperationTokenType()) &&
                                myMatchingVisitor.match(expression.getOperand(), other.getOperand()));
  }

  @Override
  public void visitPrefixExpression(@NotNull PsiPrefixExpression expression) {
    final PsiPrefixExpression other = getExpression(PsiPrefixExpression.class, expression);
    if (other == null) return;
    myMatchingVisitor.setResult(expression.getOperationTokenType().equals(other.getOperationTokenType()) &&
                                myMatchingVisitor.match(expression.getOperand(), other.getOperand()));
  }

  @Override
  public void visitAssertStatement(@NotNull PsiAssertStatement statement) {
    final PsiAssertStatement other = (PsiAssertStatement)myMatchingVisitor.getElement();

    myMatchingVisitor.setResult(myMatchingVisitor.match(statement.getAssertCondition(), other.getAssertCondition()) &&
                                myMatchingVisitor.matchOptionally(statement.getAssertDescription(), other.getAssertDescription()));
  }

  @Override
  public void visitBreakStatement(@NotNull PsiBreakStatement statement) {
    final PsiBreakStatement other = (PsiBreakStatement)myMatchingVisitor.getElement();

    myMatchingVisitor.setResult(myMatchingVisitor.matchOptionally(statement.getLabelIdentifier(), other.getLabelIdentifier()));
  }

  @Override
  public void visitContinueStatement(@NotNull PsiContinueStatement statement) {
    final PsiContinueStatement other = (PsiContinueStatement)myMatchingVisitor.getElement();

    myMatchingVisitor.setResult(myMatchingVisitor.matchOptionally(statement.getLabelIdentifier(), other.getLabelIdentifier()));
  }

  @Override
  public void visitSuperExpression(@NotNull PsiSuperExpression expression) {
    getExpression(PsiSuperExpression.class, expression);
  }

  @Override
  public void visitThisExpression(@NotNull PsiThisExpression expression) {
    getExpression(PsiThisExpression.class, expression);
  }

  @Override
  public void visitSynchronizedStatement(@NotNull PsiSynchronizedStatement statement) {
    final PsiSynchronizedStatement other = (PsiSynchronizedStatement)myMatchingVisitor.getElement();

    myMatchingVisitor.setResult(myMatchingVisitor.match(statement.getLockExpression(), other.getLockExpression()) &&
                                myMatchingVisitor.matchSons(statement.getBody(), other.getBody()));
  }

  @Override
  public void visitThrowStatement(@NotNull PsiThrowStatement statement) {
    final PsiThrowStatement other = (PsiThrowStatement)myMatchingVisitor.getElement();

    myMatchingVisitor.setResult(myMatchingVisitor.match(statement.getException(), other.getException()));
  }

  @Override
  public void visitParenthesizedExpression(@NotNull PsiParenthesizedExpression expression) {
    final PsiElement other = myMatchingVisitor.getElement();
    if (myMatchingVisitor.setResult(other instanceof PsiParenthesizedExpression)) {
      myMatchingVisitor.setResult(myMatchingVisitor.matchSons(expression, other));
    }
  }

  @Override
  public void visitCatchSection(@NotNull PsiCatchSection section) {
    final PsiCatchSection other = (PsiCatchSection)myMatchingVisitor.getElement();
    final MatchingHandler handler = myMatchingVisitor.getMatchContext().getPattern().getHandler(section);
    myMatchingVisitor.setResult(myMatchingVisitor.match(section.getParameter(), other.getParameter()) &&
                                myMatchingVisitor.matchSons(section.getCatchBlock(), other.getCatchBlock()) &&
                                ((SubstitutionHandler)handler).handle(other, myMatchingVisitor.getMatchContext()));
  }

  @Override
  public void visitTryStatement(@NotNull PsiTryStatement statement) {
    final PsiTryStatement other = (PsiTryStatement)myMatchingVisitor.getElement();

    if (!myMatchingVisitor.setResult(myMatchingVisitor.matchSons(statement.getTryBlock(), other.getTryBlock()))) return;

    final PsiResourceList resourceList1 = statement.getResourceList();
    final PsiCatchSection[] catches1 = statement.getCatchSections();
    final PsiCodeBlock finally1 = statement.getFinallyBlock();

    final PsiResourceList resourceList2 = other.getResourceList();
    final PsiCatchSection[] catches2 = other.getCatchSections();
    final PsiCodeBlock finally2 = other.getFinallyBlock();

    final MatchContext context = myMatchingVisitor.getMatchContext();
    if (myMatchingVisitor.setResult(context.getOptions().isLooseMatching() ||
                                    ((catches1.length != 0 || catches2.length == 0) &&
                                     (finally1 != null || finally2 == null) &&
                                     (resourceList1 != null || resourceList2 == null)))) {
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
        if (!myMatchingVisitor.setResult(myMatchingVisitor.matchInAnyOrder(catches1, catches2))) {
          return;
        }
      } finally {
        context.popMatchedElementsListener();
      }

      if (finally1 != null) {
        myMatchingVisitor.setResult(myMatchingVisitor.matchSons(finally1, finally2));
      } else if (finally2 != null) {
        unmatchedElements.add(finally2);
      }

      if (myMatchingVisitor.getResult()) {
        other.putUserData(GlobalMatchingVisitor.UNMATCHED_ELEMENTS_KEY, unmatchedElements);
      }
    }
  }

  @Override
  public void visitResourceExpression(@NotNull PsiResourceExpression expression) {
    final PsiElement other = myMatchingVisitor.getElement();
    myMatchingVisitor.setResult(other instanceof PsiResourceExpression &&
                                myMatchingVisitor.match(expression.getExpression(), ((PsiResourceExpression)other).getExpression()));
  }

  @Override
  public void visitLabeledStatement(@NotNull PsiLabeledStatement statement) {
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
  public void visitInstanceOfExpression(@NotNull PsiInstanceOfExpression expression) {
    final PsiInstanceOfExpression other = getExpression(PsiInstanceOfExpression.class, expression);
    if (other == null) return;
    if (!myMatchingVisitor.setResult(myMatchingVisitor.match(expression.getOperand(), other.getOperand()))) return;
    PsiTypeElement expressionType = InstanceOfUtils.findCheckTypeElement(expression);
    PsiTypeElement otherType = InstanceOfUtils.findCheckTypeElement(other);
    if (!myMatchingVisitor.setResult(myMatchingVisitor.match(expressionType, otherType))) return;
    final PsiPattern pattern = expression.getPattern();
    PsiPattern otherPattern = other.getPattern();
    if (pattern instanceof PsiTypeTestPattern typeTestPattern) {
      otherPattern = skipParenthesesDown(otherPattern);
      if (otherPattern instanceof PsiTypeTestPattern otherVariable) {
        myMatchingVisitor.setResult(
          myMatchingVisitor.matchOptionally(typeTestPattern.getPatternVariable(), otherVariable.getPatternVariable()));
      }
      else {
        myMatchingVisitor.setResult(myMatchingVisitor.allowsAbsenceOfMatch(typeTestPattern.getPatternVariable()));
      }
    }
    else {
      myMatchingVisitor.setResult(myMatchingVisitor.match(pattern, otherPattern));
    }
  }

  private PsiPattern skipParenthesesDown(PsiPattern pattern) {
    if (!myMatchingVisitor.getMatchContext().getOptions().isLooseMatching()) {
      return pattern;
    }
    return JavaPsiPatternUtil.skipParenthesizedPatternDown(pattern);
  }

  @Override
  public void visitParenthesizedPattern(@NotNull PsiParenthesizedPattern pattern) {
    final PsiParenthesizedPattern other = myMatchingVisitor.getElement(PsiParenthesizedPattern.class);
    myMatchingVisitor.setResult(other != null && myMatchingVisitor.match(pattern.getPattern(), other.getPattern()));
  }

  @Override
  public void visitTypeTestPattern(@NotNull PsiTypeTestPattern pattern) {
    final PsiTypeTestPattern other = myMatchingVisitor.getElement(PsiTypeTestPattern.class);
    final PsiPatternVariable variable = pattern.getPatternVariable();
    myMatchingVisitor.setResult(myMatchingVisitor.matchOptionally(variable, other.getPatternVariable()));
  }

  @Override
  public void visitNewExpression(@NotNull PsiNewExpression expression) {
    final PsiExpression other = getExpression(PsiExpression.class, expression);
    final PsiJavaCodeReferenceElement classReference = expression.getClassReference();
    if (other instanceof PsiArrayInitializerExpression &&
        other.getParent() instanceof PsiVariable &&
        areZeroLiterals(expression.getArrayDimensions())
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
        final PsiType type = expression.getType();
        myMatchingVisitor.setResult(type != null && type.equals(otherType));
      }
      if (myMatchingVisitor.getResult()) {
        final PsiArrayInitializerExpression initializer = expression.getArrayInitializer();
        if (initializer != null) {
          myMatchingVisitor.setResult(myMatchingVisitor.matchSons(initializer, other));
        }
        else {
          myMatchingVisitor.setResult(((PsiArrayInitializerExpression)other).getInitializers().length == 0);
        }
      }
      return;
    }

    if (!myMatchingVisitor.setResult(other instanceof PsiNewExpression)) {
      return;
    }
    final PsiNewExpression new2 = (PsiNewExpression)other;

    final PsiJavaCodeReferenceElement otherClassReference = new2.getClassReference();
    if (classReference != null) {
      if (otherClassReference != null) {
        if (myMatchingVisitor.setResult(myMatchingVisitor.matchOptionally(classReference, otherClassReference))) {
          matchArrayOrArguments(expression, new2);
        }
        return;
      }
      else {
        // match array of primitive by new 'T();
        final PsiKeyword newKeyword = PsiTreeUtil.getChildOfType(new2, PsiKeyword.class);
        final PsiKeyword typeKeyword = PsiTreeUtil.getNextSiblingOfType(newKeyword, PsiKeyword.class);

        if (typeKeyword != null ) {
          if (myMatchingVisitor.setResult(myMatchingVisitor.matchOptionally(classReference, typeKeyword))) {
            matchArrayOrArguments(expression, new2);
          }
          return;
        }
      }
    }

    final PsiAnonymousClass anonymousClass = expression.getAnonymousClass();
    if (classReference == otherClassReference) {
      //myMatchingVisitor.setResult(myMatchingVisitor.matchSons(expression, new2));
      if (anonymousClass != null) { // anonymous class
        myMatchingVisitor.setResult(matchTypeParameters(expression, new2) &&
                                    myMatchingVisitor.matchSons(expression.getArgumentList(), new2.getArgumentList()) &&
                                    myMatchingVisitor.match(anonymousClass, new2.getAnonymousClass()));
      }
      else { // array of primitive type
        final PsiType type1 = expression.getType();
        final PsiType type2 = new2.getType();
        if (myMatchingVisitor.setResult(type1 != null && type2 != null && type1.getDeepComponentType() == type2.getDeepComponentType())) {
          matchArrayOrArguments(expression, new2);
        }
      }
    }
    else if (anonymousClass == null && classReference != null && new2.getAnonymousClass() != null) {
      // allow matching anonymous class without pattern
      myMatchingVisitor.setResult(myMatchingVisitor.match(classReference, new2.getAnonymousClass().getBaseClassReference()) &&
                                  myMatchingVisitor.matchSons(expression.getArgumentList(), new2.getArgumentList()) &&
                                  matchTypeParameters(expression, new2));
    }
    else {
      myMatchingVisitor.setResult(false);
    }
  }

  @Override
  public void visitKeyword(@NotNull PsiKeyword keyword) {
    myMatchingVisitor.setResult(myMatchingVisitor.matchText(keyword, myMatchingVisitor.getElement()));
  }

  @Override
  public void visitTypeCastExpression(@NotNull PsiTypeCastExpression expression) {
    final PsiTypeCastExpression other = getExpression(PsiTypeCastExpression.class, expression);
    if (other != null) {
      myMatchingVisitor.setResult(myMatchingVisitor.match(expression.getCastType(), other.getCastType()) &&
                                  myMatchingVisitor.match(expression.getOperand(), other.getOperand()));
    }
  }

  @Override
  public void visitClassObjectAccessExpression(@NotNull PsiClassObjectAccessExpression expression) {
    final PsiClassObjectAccessExpression other = getExpression(PsiClassObjectAccessExpression.class, expression);
    if (other != null) {
      myMatchingVisitor.setResult(myMatchingVisitor.match(expression.getOperand(), other.getOperand()));
    }
  }

  @Override
  public void visitReferenceElement(@NotNull PsiJavaCodeReferenceElement ref) {
    final PsiElement other = myMatchingVisitor.getElement();
    myMatchingVisitor.setResult(matchAnnotations(ref, other) && matchType(ref, other));
  }

  @Override
  public void visitTypeElement(@NotNull PsiTypeElement typeElement) {
    final PsiElement other = myMatchingVisitor.getElement(); // might not be a PsiTypeElement
    if (!myMatchingVisitor.setResult(matchAnnotations(typeElement, other))) {
      return;
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

  private boolean matchAnnotations(@NotNull PsiElement pattern, PsiElement matched) {
    // can't use PsiAnnotationOwner api because it is not implemented completely yet (see e.g., ClsTypeParameterImpl)
    final PsiAnnotation[] annotations = PsiTreeUtil.getChildrenOfType(pattern, PsiAnnotation.class);
    if (annotations == null) {
      return true;
    }
    final PsiAnnotation[] otherAnnotations = PsiTreeUtil.getChildrenOfType(matched, PsiAnnotation.class);
    return otherAnnotations != null && myMatchingVisitor.matchInAnyOrder(annotations, otherAnnotations);
  }

  @Override
  public void visitTypeParameter(@NotNull PsiTypeParameter parameter) {
    final PsiTypeParameter other = (PsiTypeParameter)myMatchingVisitor.getElement();
    final PsiIdentifier identifier = parameter.getNameIdentifier();
    assert identifier != null;
    final PsiIdentifier identifier2 = other.getNameIdentifier();

    final MatchingHandler handler = myMatchingVisitor.getMatchContext().getPattern().getHandler(identifier);
    if (handler instanceof SubstitutionHandler) {
      if (!myMatchingVisitor.setResult(((SubstitutionHandler)handler).handle(identifier2, myMatchingVisitor.getMatchContext()))) return;
    }
    else if (!myMatchingVisitor.setResult(myMatchingVisitor.matchText(identifier, identifier2))) return;

    if (!myMatchingVisitor.setResult(matchInAnyOrder(parameter.getExtendsList(), other.getExtendsList()))) return;
    myMatchingVisitor.setResult(myMatchingVisitor.matchInAnyOrder(parameter.getAnnotations(), other.getAnnotations()));
  }

  @Override
  public void visitClass(@NotNull PsiClass clazz) {
    final PsiClass other = myMatchingVisitor.getElement(PsiClass.class);
    if (other == null) return;
    if (clazz.hasTypeParameters()) {
      if (!myMatchingVisitor.setResult(myMatchingVisitor.match(clazz.getTypeParameterList(), other.getTypeParameterList()))) return;
    }

    final PsiDocComment comment = clazz.getDocComment();
    if (comment != null) {
      if (!myMatchingVisitor.setResult(myMatchingVisitor.match(comment, other.getDocComment()))) return;
    }

    final PsiIdentifier identifier1 = clazz.getNameIdentifier();
    final MatchContext context = myMatchingVisitor.getMatchContext();
    final CompiledPattern pattern = context.getPattern();
    final boolean isTypedVar = pattern.isTypedVar(identifier1);

    final PsiModifierList modifierList = clazz.getModifierList();
    if (modifierList != null && modifierList.getTextLength() > 0) {
      if (!myMatchingVisitor.setResult(myMatchingVisitor.match(modifierList, other.getModifierList()))) {
        return;
      }
    }

    final PsiIdentifier identifier2 = other.getNameIdentifier();
    if (myMatchingVisitor.setResult((isTypedVar || myMatchingVisitor.matchText(identifier1, identifier2)) &&
                                    matchClasses(clazz, other)) && isTypedVar) {
      final PsiElement matchElement = identifier2 == null ? other : identifier2;
      final SubstitutionHandler handler = (SubstitutionHandler)pattern.getHandler(identifier1);
      final PsiElement result = other instanceof PsiAnonymousClass
                                ? ((PsiAnonymousClass)other).getBaseClassReference().getReferenceNameElement()
                                : identifier2;
      if (handler.isSubtype() || handler.isStrictSubtype()) {
        if (myMatchingVisitor.setResult(matchWithinHierarchy(identifier1, other, handler))) {
          handler.addResult(result == null ? other : result, context);
        }
      }
      else if (myMatchingVisitor.setResult(handler.validate(matchElement, context))) {
        handler.addResult(result == null ? other : result, context);
      }
    }
  }

  @Override
  public void visitTypeParameterList(@NotNull PsiTypeParameterList psiTypeParameterList) {
    myMatchingVisitor.setResult(myMatchingVisitor.matchSequentially(psiTypeParameterList.getFirstChild(),
                                                                    myMatchingVisitor.getElement().getFirstChild()));
  }

  @Override
  public void visitMethod(@NotNull PsiMethod method) {
    final PsiIdentifier methodNameNode = method.getNameIdentifier();
    final MatchContext context = myMatchingVisitor.getMatchContext();
    final boolean isTypedVar = context.getPattern().isTypedVar(methodNameNode);
    final PsiMethod other = myMatchingVisitor.getElement(PsiMethod.class);
    if (other == null) return;

    context.pushResult();
    try {
      final PsiDocComment docComment = method.getDocComment();
      if (docComment != null && !myMatchingVisitor.setResult(myMatchingVisitor.match(docComment, other.getDocComment()))) return;
      if (method.hasTypeParameters() && !myMatchingVisitor.setResult(
        myMatchingVisitor.match(method.getTypeParameterList(), other.getTypeParameterList()))) return;

      if (!myMatchingVisitor.setResult(checkHierarchy(other, method))) {
        return;
      }

      if (!myMatchingVisitor.setResult((!method.isConstructor() || other.isConstructor()) &&
                                       (isTypedVar || myMatchingVisitor.matchText(methodNameNode, other.getNameIdentifier())) &&
                                       myMatchingVisitor.match(method.getModifierList(), other.getModifierList()))) {
        return;
      }
      final PsiParameterList otherParameterList = other.getParameterList();
      final PsiReceiverParameter receiverParameter = PsiTreeUtil.findChildOfType(otherParameterList, PsiReceiverParameter.class);
      if (receiverParameter != null) {
        final PsiParameterList parameterList = method.getParameterList();
        final PsiVariable firstParameter = PsiTreeUtil.findChildOfType(parameterList, PsiVariable.class);
        if (firstParameter != null) {
          final MatchingHandler handler = myMatchingVisitor.getMatchContext().getPattern().getHandler(firstParameter);
          if (handler instanceof SubstitutionHandler substHandler &&
              substHandler.handle(receiverParameter, context) &&
              !myMatchingVisitor.setResult(substHandler.getMaxOccurs() != 0)) {
            return;
          }
        }
        if (!myMatchingVisitor.setResult(myMatchingVisitor.matchSons(method.getParameterList(), otherParameterList) ||
                                         myMatchingVisitor.matchSequentially(parameterList.getFirstChild(),
                                                                             receiverParameter.getNextSibling()))) {
          return;
        }
      }
      else if (!myMatchingVisitor.setResult(myMatchingVisitor.matchSons(method.getParameterList(), otherParameterList))) {
        return;
      }
      myMatchingVisitor.setResult(myMatchingVisitor.matchOptionally(method.getReturnTypeElement(), other.getReturnTypeElement()) &&
                                  matchInAnyOrder(method.getThrowsList(), other.getThrowsList()) &&
                                  myMatchingVisitor.matchSonsOptionally(method.getBody(), other.getBody()));
    }
    finally {
      myMatchingVisitor.scopeMatch(methodNameNode, isTypedVar, other.getNameIdentifier());
    }
  }

  @Nullable
  private <T extends PsiExpression> T getExpression(@NotNull Class<T> aClass, @NotNull PsiExpression patternExpression) {
    PsiExpression expression = myMatchingVisitor.getElement(PsiExpression.class);
    if (!(patternExpression.getParent() instanceof PsiExpressionStatement)) {
      expression = PsiUtil.skipParenthesizedExprDown(expression);
    }
    return myMatchingVisitor.setResult(aClass.isInstance(expression)) ? aClass.cast(expression) : null;
  }
}

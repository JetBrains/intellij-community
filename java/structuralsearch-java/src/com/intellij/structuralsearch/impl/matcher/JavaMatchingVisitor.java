package com.intellij.structuralsearch.impl.matcher;

import com.intellij.dupLocator.iterators.ArrayBackedNodeIterator;
import com.intellij.dupLocator.iterators.NodeIterator;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.structuralsearch.MatchOptions;
import com.intellij.structuralsearch.MatchResult;
import com.intellij.structuralsearch.impl.matcher.filters.LexicalNodesFilter;
import com.intellij.structuralsearch.impl.matcher.handlers.MatchPredicate;
import com.intellij.structuralsearch.impl.matcher.handlers.MatchingHandler;
import com.intellij.structuralsearch.impl.matcher.handlers.SubstitutionHandler;
import com.intellij.structuralsearch.impl.matcher.iterators.DocValuesIterator;
import com.intellij.structuralsearch.impl.matcher.iterators.HierarchyNodeIterator;
import com.intellij.structuralsearch.impl.matcher.predicates.NotPredicate;
import com.intellij.structuralsearch.impl.matcher.predicates.RegExpPredicate;
import com.intellij.util.containers.ContainerUtil;
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
  public static final Key<List<PsiCatchSection>> UNMATCHED_CATCH_SECTION_CONTENT_VAR_KEY = Key.create("UnmatchedCatchSection");
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

  private static boolean isNotInstanceModifier(final PsiModifierList list2) {
    return list2.hasModifierProperty(PsiModifier.STATIC) ||
           list2.hasModifierProperty(PsiModifier.ABSTRACT);
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
      HashSet<PsiAnnotation> set = new HashSet<PsiAnnotation>(Arrays.asList(annotations));

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
                @PsiModifier.ModifierConstant String name = StringUtil.stripQuotesAroundValue(v.getText());
                if (MatchOptions.INSTANCE_MODIFIER_NAME.equals(name)) {
                  if (isNotInstanceModifier(list2)) {
                    myMatchingVisitor.setResult(false);
                    return;
                  }
                  else {
                    matchedOne = true;
                  }
                }
                else if (list2.hasModifierProperty(name)) {
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
              @PsiModifier.ModifierConstant String name = StringUtil.stripQuotesAroundValue(value.getText());
              if (MatchOptions.INSTANCE_MODIFIER_NAME.equals(name)) {
                if (isNotInstanceModifier(list2)) {
                  myMatchingVisitor.setResult(false);
                  return;
                }
              }
              else if (!list2.hasModifierProperty(name)) {
                myMatchingVisitor.setResult(false);
                return;
              }
            }
          }

          set.remove(annotation);
        }
      }

      myMatchingVisitor.setResult(set.isEmpty() || myMatchingVisitor
        .matchInAnyOrder(set.toArray(new PsiAnnotation[set.size()]), list2.getAnnotations()));
    }
    else {
      myMatchingVisitor.setResult(true);
    }
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
          myMatchingVisitor.setResult(allowsAbsenceOfMatch(psiDocTagValue));
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

  private boolean allowsAbsenceOfMatch(final PsiElement element) {
    MatchingHandler handler = myMatchingVisitor.getMatchContext().getPattern().getHandler(element);

    if (handler instanceof SubstitutionHandler &&
        ((SubstitutionHandler)handler).getMinOccurs() == 0) {
      return true;
    }
    return false;
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
                                myMatchingVisitor.match(initializer.getBody(), initializer2.getBody()));
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
    final PsiAnnotation psiAnnotation = (PsiAnnotation)myMatchingVisitor.getElement();

    myMatchingVisitor.setResult(myMatchingVisitor.match(annotation.getNameReferenceElement(), psiAnnotation.getNameReferenceElement()) &&
                                myMatchingVisitor
                                  .matchInAnyOrder(annotation.getParameterList().getAttributes(),
                                                   psiAnnotation.getParameterList().getAttributes()));
  }

  @Override
  public void visitNameValuePair(PsiNameValuePair pair) {
    final PsiNameValuePair elementNameValuePair = (PsiNameValuePair)myMatchingVisitor.getElement();

    final PsiAnnotationMemberValue annotationInitializer = pair.getValue();
    if (annotationInitializer != null) {
      final boolean isTypedInitializer = myMatchingVisitor.getMatchContext().getPattern().isTypedVar(annotationInitializer) &&
                                         annotationInitializer instanceof PsiReferenceExpression;

      myMatchingVisitor.setResult(myMatchingVisitor.match(annotationInitializer, elementNameValuePair.getValue()) ||
                                  (isTypedInitializer &&
                                   elementNameValuePair.getValue() == null &&
                                   allowsAbsenceOfMatch(annotationInitializer)
                                  ));
    }
    if (myMatchingVisitor.getResult()) {
      final PsiIdentifier nameIdentifier = pair.getNameIdentifier();
      final PsiIdentifier otherIdentifier = elementNameValuePair.getNameIdentifier();
      if (nameIdentifier == null) {
        myMatchingVisitor.setResult(otherIdentifier == null ||
                                    otherIdentifier.getText().equals(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME));
      }
      else {
        final MatchingHandler handler = myMatchingVisitor.getMatchContext().getPattern().getHandler(nameIdentifier);
        if (handler instanceof SubstitutionHandler) {
          myMatchingVisitor.setResult(((SubstitutionHandler)handler).handle(otherIdentifier, myMatchingVisitor.getMatchContext()));
        }
        else {
          myMatchingVisitor.setResult(myMatchingVisitor.match(nameIdentifier, otherIdentifier));
        }
      }
    }
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
    return element.getContainingClass() == myClazz;
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
    final MatchContext.MatchedElementsListener oldListener = myMatchingVisitor.getMatchContext().getMatchedElementsListener();

    this.myClazz = clazz2;

    final CompiledPattern pattern = myMatchingVisitor.getMatchContext().getPattern();
    assert pattern instanceof JavaCompiledPattern;
    final JavaCompiledPattern javaPattern = (JavaCompiledPattern)pattern;

    final String unmatchedHandlerName = clazz.getUserData(JavaCompiledPattern.ALL_CLASS_CONTENT_VAR_NAME_KEY);
    final MatchingHandler allRemainingClassContentElementHandler = unmatchedHandlerName != null ? pattern.getHandler(unmatchedHandlerName) : null;
    MatchContext.MatchedElementsListener newListener = null;

    assert javaPattern instanceof JavaCompiledPattern;
    if (allRemainingClassContentElementHandler != null) {
      myMatchingVisitor.getMatchContext().setMatchedElementsListener(
        newListener = new MatchContext.MatchedElementsListener() {
          private Set<PsiElement> myMatchedElements;

          public void matchedElements(Collection<PsiElement> matchedElements) {
            if (matchedElements == null) return;
            if (myMatchedElements == null) {
              myMatchedElements = new HashSet<PsiElement>(matchedElements);
            }
            else {
              myMatchedElements.addAll(matchedElements);
            }
          }

          public void commitUnmatched() {
            final SubstitutionHandler handler = (SubstitutionHandler)allRemainingClassContentElementHandler;

            for (PsiElement el = clazz2.getFirstChild(); el != null; el = el.getNextSibling()) {
              if (el instanceof PsiMember && (myMatchedElements == null || !myMatchedElements.contains(el))) {
                handler.handle(el, myMatchingVisitor.getMatchContext());
              }
            }
          }
        }
      );
    }

    boolean result = false;
    try {
      final boolean templateIsInterface = clazz.isInterface();
      if (templateIsInterface != clazz2.isInterface()) return false;
      if (templateIsInterface && clazz.isAnnotationType() && !clazz2.isAnnotationType()) return false;
      if (clazz.isEnum() && !clazz2.isEnum()) return false;

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

      result = true;
      return result;
    }
    finally {
      if (result && newListener != null) newListener.commitUnmatched();
      this.myClazz = saveClazz;
      myMatchingVisitor.getMatchContext().setMatchedElementsListener(oldListener);
    }
  }

  private boolean compareBody(final PsiElement el1, final PsiElement el2) {
    PsiElement compareElement1 = el1;
    PsiElement compareElement2 = el2;

    if (myMatchingVisitor.getMatchContext().getOptions().isLooseMatching()) {
      if (el1 instanceof PsiBlockStatement) {
        compareElement1 = ((PsiBlockStatement)el1).getCodeBlock().getFirstChild();
      }

      if (el2 instanceof PsiBlockStatement) {
        compareElement2 = ((PsiBlockStatement)el2).getCodeBlock().getFirstChild();
      }
    }

    return myMatchingVisitor.matchSequentially(compareElement1, compareElement2);
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
    final PsiExpression qualifier = reference.getQualifierExpression();

    final PsiElement nameElement = reference.getReferenceNameElement();
    final MatchContext context = myMatchingVisitor.getMatchContext();
    MatchingHandler _handler = nameElement != null ? context.getPattern().getHandlerSimple(nameElement) : null;
    if (!(_handler instanceof SubstitutionHandler)) _handler = context.getPattern().getHandlerSimple(reference);

    final PsiElement element = myMatchingVisitor.getElement();
    PsiElement other = element instanceof PsiExpression && context.getOptions().isLooseMatching() ?
                       PsiUtil.skipParenthesizedExprDown((PsiExpression)element) :
                       element;
    if (_handler instanceof SubstitutionHandler &&
        !(context.getPattern().getHandlerSimple(qualifier) instanceof SubstitutionHandler) &&
        !(qualifier instanceof PsiThisExpression)
      ) {
      if (other instanceof PsiReferenceExpression) {
        final PsiReferenceExpression psiReferenceExpression = (PsiReferenceExpression)other;

        final PsiExpression qualifier2 = psiReferenceExpression.getQualifierExpression();
        if (qualifier2 == null || (context.getOptions().isLooseMatching() && qualifier2 instanceof PsiThisExpression)) {
          other = psiReferenceExpression.getReferenceNameElement();
        }
      }

      final SubstitutionHandler handler = (SubstitutionHandler)_handler;
      if (handler.isSubtype() || handler.isStrictSubtype()) {
        myMatchingVisitor.setResult(checkMatchWithingHierarchy(other, handler, reference));
      }
      else {
        myMatchingVisitor.setResult(handler.handle(other, context));
      }

      return;
    }

    if (!(other instanceof PsiReferenceExpression)) {
      myMatchingVisitor.setResult(false);
      return;
    }

    final PsiReferenceExpression reference2 = (PsiReferenceExpression)other;

    // just variable
    final PsiExpression reference2Qualifier = reference2.getQualifierExpression();
    if (qualifier == null && reference2Qualifier == null) {
      myMatchingVisitor.setResult(myMatchingVisitor.matchText(reference.getReferenceNameElement(), reference2.getReferenceNameElement()));
      return;
    }

    // handle field selection
    if (!(other.getParent() instanceof PsiMethodCallExpression) && qualifier != null) {
      final PsiElement referenceElement = reference.getReferenceNameElement();
      final PsiElement referenceElement2 = reference2.getReferenceNameElement();

      if (context.getPattern().isTypedVar(referenceElement)) {
        myMatchingVisitor.setResult(myMatchingVisitor.handleTypedElement(referenceElement, referenceElement2));
      }
      else {
        myMatchingVisitor.setResult(myMatchingVisitor.matchText(referenceElement, referenceElement2));
      }

      if (!myMatchingVisitor.getResult()) {
        return;
      }
      if (reference2Qualifier != null) {
        myMatchingVisitor.setResult(myMatchingVisitor.match(qualifier, reference2Qualifier));
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
        final MatchingHandler handler = context.getPattern().getHandler(qualifier);
        matchImplicitQualifier(handler, referencedElement, context);
      }

      return;
    }

    myMatchingVisitor.setResult(false);
  }

  private static int countCStyleArrayDeclarationDims(final PsiElement type2) {
    if (type2 != null) {
      final PsiElement parentElement = type2.getParent();

      if (parentElement instanceof PsiVariable) {
        final PsiIdentifier psiIdentifier = ((PsiVariable)parentElement).getNameIdentifier();
        if (psiIdentifier == null) return 0;

        int count = 0;
        for (PsiElement sibling = psiIdentifier.getNextSibling(); sibling != null; sibling = sibling.getNextSibling()) {
          if (sibling instanceof PsiJavaToken) {
            final IElementType tokenType = ((PsiJavaToken)sibling).getTokenType();
            if (tokenType == JavaTokenType.LBRACKET) ++count;
            else if (tokenType != JavaTokenType.RBRACKET) break;
          }
        }

        return count;
      }
    }
    return 0;
  }

  private void copyResults(final MatchResultImpl ourResult) {
    if (ourResult.hasSons()) {
      for (MatchResult son : ourResult.getAllSons()) {
        myMatchingVisitor.getMatchContext().getResult().addSon((MatchResultImpl)son);
      }
    }
  }

  private boolean matchType(final PsiElement _type, final PsiElement _type2) {
    PsiElement el = _type;
    PsiElement el2 = _type2;
    PsiType type1 = null;
    PsiType type2 = null;

    // check for generics
    if (_type instanceof PsiTypeElement &&
        ((PsiTypeElement)_type).getInnermostComponentReferenceElement() != null
      ) {
      el = ((PsiTypeElement)_type).getInnermostComponentReferenceElement();
      type1 = ((PsiTypeElement)_type).getType();
    }

    if (_type2 instanceof PsiTypeElement &&
        ((PsiTypeElement)_type2).getInnermostComponentReferenceElement() != null
      ) {
      el2 = ((PsiTypeElement)_type2).getInnermostComponentReferenceElement();
      type2 = ((PsiTypeElement)_type2).getType();
    }

    PsiElement[] typeparams = null;
    if (el2 instanceof PsiJavaCodeReferenceElement) {
      final PsiReferenceParameterList parameterList = ((PsiJavaCodeReferenceElement)el2).getParameterList();
      if (parameterList != null) {
        typeparams = parameterList.getTypeParameterElements();
      }
    }
    else if (el2 instanceof PsiTypeParameter) {
      el2 = ((PsiTypeParameter)el2).getNameIdentifier();
    }
    else if (el2 instanceof PsiClass && ((PsiClass)el2).hasTypeParameters()) {
      typeparams = ((PsiClass)el2).getTypeParameters();
      el2 = ((PsiClass)el2).getNameIdentifier();
    }
    else if (el2 instanceof PsiMethod && ((PsiMethod)el2).hasTypeParameters()) {
      typeparams = ((PsiMethod)_type2).getTypeParameters();
      el2 = ((PsiMethod)_type2).getNameIdentifier();
    }

    PsiReferenceParameterList list = null;
    if (el instanceof PsiJavaCodeReferenceElement) {
      list = ((PsiJavaCodeReferenceElement)el).getParameterList();
      el = ((PsiJavaCodeReferenceElement)el).getReferenceNameElement();
    }

    if (list != null && list.getTypeParameterElements().length > 0) {
      boolean result = typeparams != null && myMatchingVisitor.matchSequentially(list.getTypeParameterElements(), typeparams);

      if (!result) return false;
    }
    else {
      if (_type2 instanceof PsiTypeElement) {
        type2 = ((PsiTypeElement)_type2).getType();

        if (typeparams == null || typeparams.length == 0) {
          final PsiJavaCodeReferenceElement innermostComponentReferenceElement =
            ((PsiTypeElement)_type2).getInnermostComponentReferenceElement();
          if (innermostComponentReferenceElement != null) el2 = innermostComponentReferenceElement;
        }
        else {
          el2 = _type2;
        }
      }
    }

    final int array2Dims = (type2 != null ? type2.getArrayDimensions() : 0) + countCStyleArrayDeclarationDims(_type2);
    final int arrayDims = (type1 != null ? type1.getArrayDimensions() : 0) + countCStyleArrayDeclarationDims(_type);

    if (myMatchingVisitor.getMatchContext().getPattern().isTypedVar(el)) {
      final SubstitutionHandler handler = (SubstitutionHandler)myMatchingVisitor.getMatchContext().getPattern().getHandler(el);

      RegExpPredicate regExpPredicate = null;

      if (arrayDims != 0) {
        if (arrayDims != array2Dims) {
          return false;
        }
      }
      else if (array2Dims != 0) {
        regExpPredicate = MatchingHandler.getSimpleRegExpPredicate(handler);

        if (regExpPredicate != null) {
          regExpPredicate.setNodeTextGenerator(new RegExpPredicate.NodeTextGenerator() {
            public String getText(PsiElement element) {
              StringBuilder builder = new StringBuilder(RegExpPredicate.getMeaningfulText(element));
              for (int i = 0; i < array2Dims; ++i) builder.append("[]");
              return builder.toString();
            }
          });
        }
      }

      try {
        if (handler.isSubtype() || handler.isStrictSubtype()) {
          return checkMatchWithingHierarchy(el2, handler, el);
        }
        else {
          return handler.handle(el2, myMatchingVisitor.getMatchContext());
        }
      }
      finally {
        if (regExpPredicate != null) regExpPredicate.setNodeTextGenerator(null);
      }
    }

    if (array2Dims != arrayDims) {
      return false;
    }

    if (el instanceof PsiIdentifier) {
      final PsiElement parent = el.getParent();
      if (parent instanceof PsiJavaCodeReferenceElement) {
        el = parent;
      }
    }
    if (el2 instanceof PsiIdentifier) {
      final PsiElement parent = el2.getParent();
      if (parent instanceof PsiJavaCodeReferenceElement) {
        el2 = parent;
      }
    }
    final String text = getText(el);
    final String text2 = getText(el2);
    final boolean caseSensitive = myMatchingVisitor.getMatchContext().getOptions().isCaseSensitiveMatch();
    final boolean equalsIgnorePackage = MatchUtils.compareWithNoDifferenceToPackage(text, text2, !caseSensitive);
    if (equalsIgnorePackage || !(el2 instanceof PsiJavaReference)) {
      return equalsIgnorePackage;
    }
    else {
      final PsiElement element2 = ((PsiJavaReference)el2).resolve();

      if (element2 instanceof PsiClass) {
        final String name = ((PsiClass)element2).getQualifiedName();
        return caseSensitive ? text.equals(name) : text.equalsIgnoreCase(name);
      }
      else {
        return MatchUtils.compareWithNoDifferenceToPackage(text, text2, !caseSensitive);
      }
    }
  }

  @Contract(pure = true)
  private static String getText(@NotNull PsiElement element) {
    String result;
    if (element instanceof PsiClass) {
      result = ((PsiClass)element).getQualifiedName();
      if (result == null) result = element.getText();
    } else {
      result = element.getText();
    }
    final int whitespace = lastIndexOfWhitespace(result);
    if (whitespace >= 0) {
      // strips off any annotations
      result = result.substring(whitespace + 1);
    }
    final int index = result.indexOf('<');
    if (index == -1) {
      return result;
    }
    return result.substring(0, index);
  }

  @Contract(pure = true)
  private static int lastIndexOfWhitespace(@NotNull CharSequence s) {
    for (int i = s.length() - 1; i >= 0; i--) {
      if (Character.isWhitespace(s.charAt(i))) return i;
    }
    return -1;
  }

  private boolean checkMatchWithingHierarchy(PsiElement el2, SubstitutionHandler handler, PsiElement context) {
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
    PsiPolyadicExpression expr2 = (PsiPolyadicExpression)myMatchingVisitor.getElement();

    boolean result = expression.getOperationTokenType().equals(expr2.getOperationTokenType());
    if (result) {
      PsiExpression[] operands1 = expression.getOperands();
      PsiExpression[] operands2 = expr2.getOperands();
      if (operands1.length != operands2.length) {
        result = false;
      }
      else {
        for (int i = 0; i < operands1.length; i++) {
          PsiExpression e1 = operands1[i];
          PsiExpression e2 = operands2[i];
          if (!myMatchingVisitor.match(e1, e2)) {
            result = false;
            break;
          }
        }
      }
    }

    myMatchingVisitor.setResult(result);
  }

  @Override
  public void visitVariable(final PsiVariable var) {
    myMatchingVisitor.getMatchContext().pushResult();
    final PsiIdentifier nameIdentifier = var.getNameIdentifier();

    boolean isTypedVar = myMatchingVisitor.getMatchContext().getPattern().isTypedVar(nameIdentifier);
    boolean isTypedInitializer = var.getInitializer() != null &&
                                 myMatchingVisitor.getMatchContext().getPattern().isTypedVar(var.getInitializer()) &&
                                 var.getInitializer() instanceof PsiReferenceExpression;
    final PsiVariable var2 = (PsiVariable)myMatchingVisitor.getElement();

    try {
      myMatchingVisitor.setResult((myMatchingVisitor.matchText(var.getNameIdentifier(), var2.getNameIdentifier()) || isTypedVar) &&
                                  ((var.getParent() instanceof PsiClass && ((PsiClass)var.getParent()).isInterface()) ||
                                   myMatchingVisitor.match(var.getModifierList(), var2.getModifierList())));
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
        final PsiExpression var2Initializer = var2.getInitializer();

        myMatchingVisitor.setResult(myMatchingVisitor.match(var.getInitializer(), var2Initializer) ||
                                    (isTypedInitializer &&
                                     var2Initializer == null &&
                                     allowsAbsenceOfMatch(var.getInitializer())
                                    ));
      }

      if (myMatchingVisitor.getResult() && var instanceof PsiParameter && var.getParent() instanceof PsiCatchSection) {
        myMatchingVisitor.setResult(myMatchingVisitor.match(
          ((PsiCatchSection)var.getParent()).getCatchBlock(),
          ((PsiCatchSection)var2.getParent()).getCatchBlock()
        ));
      }

      if (myMatchingVisitor.getResult() && isTypedVar) {
        myMatchingVisitor.setResult(myMatchingVisitor.handleTypedElement(nameIdentifier, var2.getNameIdentifier()));
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
      myMatchingVisitor.setResult(predicate.match(null, PsiTreeUtil.getParentOfType(target, PsiClass.class), context));
    } else {
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(target.getProject());
      final PsiExpression implicitReference = factory.createExpressionFromText("this", target);
      myMatchingVisitor.setResult(predicate.match(null, implicitReference, context));
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

    final boolean isTypedVar = myMatchingVisitor.getMatchContext().getPattern().isTypedVar(mcallRef1.getReferenceNameElement());

    if (!myMatchingVisitor.matchText(mcallRef1.getReferenceNameElement(), mcallRef2.getReferenceNameElement()) && !isTypedVar) {
      myMatchingVisitor.setResult(false);
      return;
    }

    final PsiExpression qualifier = mcallRef1.getQualifierExpression();
    final PsiExpression elementQualifier = mcallRef2.getQualifierExpression();
    if (qualifier != null) {

      if (elementQualifier != null) {
        myMatchingVisitor.setResult(myMatchingVisitor.match(qualifier, elementQualifier));
        if (!myMatchingVisitor.getResult()) return;
      }
      else {
        final PsiMethod method = mcall2.resolveMethod();
        if (method != null) {
          if (qualifier instanceof PsiThisExpression) {
            myMatchingVisitor.setResult(!method.hasModifierProperty(PsiModifier.STATIC));
            return;
          }
        }
        final MatchingHandler handler = myMatchingVisitor.getMatchContext().getPattern().getHandler(qualifier);
        matchImplicitQualifier(handler, method, myMatchingVisitor.getMatchContext());
        if (!myMatchingVisitor.getResult()) {
          return;
        }
      }
    }
    else if (elementQualifier != null) {
      myMatchingVisitor.setResult(false);
      return;
    }

    myMatchingVisitor.setResult(myMatchingVisitor.matchSons(mcall.getArgumentList(), mcall2.getArgumentList()));

    if (myMatchingVisitor.getResult() && isTypedVar) {
      boolean res = myMatchingVisitor.getResult();
      res &= myMatchingVisitor.handleTypedElement(mcallRef1.getReferenceNameElement(), mcallRef2.getReferenceNameElement());
      myMatchingVisitor.setResult(res);
    }
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

        if (length > 2 && text.charAt(0) == '"' && text.charAt(length - 1) == '"') {
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
      myMatchingVisitor.setResult(myMatchingVisitor.matchText(const1, const2));
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
                                compareBody(if1.getThenBranch(), if2.getThenBranch()) &&
                                (elseBranch == null || compareBody(elseBranch, if2.getElseBranch())));
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

    final PsiStatement initialization = for1.getInitialization();
    MatchingHandler handler = myMatchingVisitor.getMatchContext().getPattern().getHandler(initialization);

    myMatchingVisitor.setResult(handler.match(initialization, for2.getInitialization(), myMatchingVisitor.getMatchContext()) &&
                                myMatchingVisitor.match(for1.getCondition(), for2.getCondition()) &&
                                myMatchingVisitor.match(for1.getUpdate(), for2.getUpdate()) &&
                                compareBody(for1.getBody(), for2.getBody()));
  }

  @Override
  public void visitForeachStatement(PsiForeachStatement for1) {
    final PsiForeachStatement for2 = (PsiForeachStatement)myMatchingVisitor.getElement();

    myMatchingVisitor.setResult(myMatchingVisitor.match(for1.getIterationParameter(), for2.getIterationParameter()) &&
                                myMatchingVisitor.match(for1.getIteratedValue(), for2.getIteratedValue()) &&
                                compareBody(for1.getBody(), for2.getBody()));
  }

  @Override
  public void visitWhileStatement(final PsiWhileStatement while1) {
    final PsiWhileStatement while2 = (PsiWhileStatement)myMatchingVisitor.getElement();

    myMatchingVisitor.setResult(myMatchingVisitor.match(while1.getCondition(), while2.getCondition()) &&
                                compareBody(while1.getBody(), while2.getBody()));
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
                                compareBody(while1.getBody(), while2.getBody()));
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
    myMatchingVisitor.setResult(true);
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
  public void visitCatchSection(final PsiCatchSection section) {
    final PsiCatchSection section2 = (PsiCatchSection)myMatchingVisitor.getElement();
    final PsiParameter parameter = section.getParameter();
    if (parameter != null) {
      myMatchingVisitor.setResult(myMatchingVisitor.match(parameter, section2.getParameter()));
    }
    else {
      myMatchingVisitor.setResult(myMatchingVisitor.match(section.getCatchBlock(), section2.getCatchBlock()));
    }
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
      if (resourceList1 != null) {
        if (resourceList2 == null) {
          myMatchingVisitor.setResult(false);
          return;
        }
        final List<PsiResourceVariable> resourceVariables1 = resourceList1.getResourceVariables();
        final List<PsiResourceVariable> resourceVariables2 = resourceList2.getResourceVariables();
        myMatchingVisitor.setResult(myMatchingVisitor.matchInAnyOrder(
          resourceVariables1.toArray(new PsiResourceVariable[resourceVariables1.size()]),
          resourceVariables2.toArray(new PsiResourceVariable[resourceVariables2.size()])));
        if (!myMatchingVisitor.getResult()) return;
      }

      final List<PsiCatchSection> unmatchedCatchSections = new ArrayList<PsiCatchSection>();

      ContainerUtil.addAll(unmatchedCatchSections, catches2);

      for (PsiCatchSection catchSection : catches1) {
        final MatchingHandler handler = myMatchingVisitor.getMatchContext().getPattern().getHandler(catchSection);
        final PsiElement pinnedNode = handler.getPinnedNode(null);

        if (pinnedNode != null) {
          myMatchingVisitor.setResult(handler.match(catchSection, pinnedNode, myMatchingVisitor.getMatchContext()));
          if (!myMatchingVisitor.getResult()) return;
        }
        else {
          int j;
          for (j = 0; j < unmatchedCatchSections.size(); ++j) {
            if (handler.match(catchSection, unmatchedCatchSections.get(j), myMatchingVisitor.getMatchContext())) {
              unmatchedCatchSections.remove(j);
              break;
            }
          }

          if (j == catches2.length) {
            myMatchingVisitor.setResult(false);
            return;
          }
        }
      }

      if (finally1 != null) {
        myMatchingVisitor.setResult(myMatchingVisitor.matchSons(finally1, finally2));
      }

      if (myMatchingVisitor.getResult() && unmatchedCatchSections.size() > 0) {
        try2.putUserData(UNMATCHED_CATCH_SECTION_CONTENT_VAR_KEY, unmatchedCatchSections);
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
        myMatchingVisitor.setResult(predicate == null || predicate.match(null, otherTypeElement, matchContext));
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
          ((LexicalNodesFilter)LexicalNodesFilter.getInstance()).setCareKeyWords(true);

          myMatchingVisitor.setResult(myMatchingVisitor.match(classReference, element.getNextSibling()) &&
                                      myMatchingVisitor.matchSons(new1.getArrayInitializer(), new2.getArrayInitializer()));

          ((LexicalNodesFilter)LexicalNodesFilter.getInstance()).setCareKeyWords(false);
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
      ((LexicalNodesFilter)LexicalNodesFilter.getInstance()).setCareKeyWords(true);
      myMatchingVisitor.setResult(myMatchingVisitor.matchSons(new1, new2));
      ((LexicalNodesFilter)LexicalNodesFilter.getInstance()).setCareKeyWords(false);
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
    if (clazz.hasTypeParameters()) {
      myMatchingVisitor
        .setResult(
          myMatchingVisitor.match(clazz.getTypeParameterList(), ((PsiClass)myMatchingVisitor.getElement()).getTypeParameterList()));

      if (!myMatchingVisitor.getResult()) return;
    }

    PsiClass clazz2;

    if (myMatchingVisitor.getElement() instanceof PsiDeclarationStatement &&
        myMatchingVisitor.getElement().getFirstChild() instanceof PsiClass
      ) {
      clazz2 = (PsiClass)myMatchingVisitor.getElement().getFirstChild();
    }
    else {
      clazz2 = (PsiClass)myMatchingVisitor.getElement();
    }

    final boolean isTypedVar = myMatchingVisitor.getMatchContext().getPattern().isTypedVar(clazz.getNameIdentifier());

    if (clazz.getModifierList().getTextLength() > 0) {
      if (!myMatchingVisitor.match(clazz.getModifierList(), clazz2.getModifierList())) {
        myMatchingVisitor.setResult(false);
        return;
      }
    }

    myMatchingVisitor.setResult((myMatchingVisitor.matchText(clazz.getNameIdentifier(), clazz2.getNameIdentifier()) || isTypedVar) &&
                                compareClasses(clazz, clazz2));

    if (myMatchingVisitor.getResult() && isTypedVar) {
      PsiElement id = clazz2.getNameIdentifier();
      if (id == null) id = clazz2;
      myMatchingVisitor.setResult(myMatchingVisitor.handleTypedElement(clazz.getNameIdentifier(), id));
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

      myMatchingVisitor.setResult((myMatchingVisitor.matchText(method.getNameIdentifier(), method2.getNameIdentifier()) || isTypedVar) &&
                                  myMatchingVisitor.match(method.getModifierList(), method2.getModifierList()) &&
                                  myMatchingVisitor.matchSons(method.getParameterList(), method2.getParameterList()) &&
                                  myMatchingVisitor.match(method.getReturnTypeElement(), method2.getReturnTypeElement()) &&
                                                    matchInAnyOrder(method.getThrowsList(), method2.getThrowsList()) &&
                                  myMatchingVisitor.matchSonsOptionally(method.getBody(), method2.getBody()));
    }
    finally {
      final PsiIdentifier methodNameNode2 = method2.getNameIdentifier();

      saveOrDropResult(methodNameNode, isTypedVar, methodNameNode2);
    }
  }
}

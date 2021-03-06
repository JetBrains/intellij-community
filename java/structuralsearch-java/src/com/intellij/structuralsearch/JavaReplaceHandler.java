// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch;

import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.structuralsearch.impl.matcher.GlobalMatchingVisitor;
import com.intellij.structuralsearch.impl.matcher.MatcherImplUtil;
import com.intellij.structuralsearch.impl.matcher.PatternTreeContext;
import com.intellij.structuralsearch.plugin.replace.ReplaceOptions;
import com.intellij.structuralsearch.plugin.replace.ReplacementInfo;
import com.intellij.structuralsearch.plugin.replace.impl.Replacer;
import com.intellij.structuralsearch.plugin.replace.impl.ReplacerUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.siyeh.ig.psiutils.ImportUtils;
import com.siyeh.ig.psiutils.PsiElementOrderComparator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * @author Eugene.Kudelevsky
 */
public class JavaReplaceHandler extends StructuralReplaceHandler {
  private final PsiElement[] patternElements;
  @NotNull private final Project myProject;
  @NotNull private final ReplaceOptions myReplaceOptions;

  public JavaReplaceHandler(@NotNull Project project, @NotNull ReplaceOptions replaceOptions) {
    myProject = project;
    myReplaceOptions = replaceOptions;
    final MatchOptions matchOptions = replaceOptions.getMatchOptions();
    final LanguageFileType fileType = matchOptions.getFileType();
    assert fileType != null;
    patternElements = MatcherImplUtil.createTreeFromText(
      matchOptions.getSearchPattern(),
      PatternTreeContext.Block,
      fileType,
      project
    );
  }

  private static boolean isListContext(PsiElement element) {
    if (element instanceof PsiParameter || element instanceof PsiClass) {
      return true;
    }
    final PsiElement parent = element.getParent();

    return (parent instanceof PsiExpressionList && !parent.getClass().getSimpleName().startsWith("Jsp")) ||
           parent instanceof PsiCodeBlock || parent instanceof PsiCodeFragment ||
           parent instanceof PsiClass ||
           parent instanceof PsiIfStatement && (((PsiIfStatement)parent).getThenBranch() == element ||
                                                ((PsiIfStatement)parent).getElseBranch() == element) ||
           parent instanceof PsiLoopStatement && ((PsiLoopStatement)parent).getBody() == element;
  }

  @Nullable
  private PsiNamedElement getSymbolReplacementTarget(final PsiElement el) {
    if (patternElements.length == 1 && patternElements[0] instanceof PsiExpressionStatement) {
      final PsiExpression expression = ((PsiExpressionStatement)patternElements[0]).getExpression();

      if (expression instanceof PsiReferenceExpression && ((PsiReferenceExpression)expression).getQualifierExpression() == null) {
        // looks like symbol replacements, namely replace AAA by BBB, so lets do the best
        if (el instanceof PsiNamedElement) {
          return (PsiNamedElement)el;
        }
      }
    }

    return null;
  }

  private static PsiElement getMatchExpr(PsiElement replacement, PsiElement elementToReplace) {
    if (replacement instanceof PsiExpressionStatement &&
        !(replacement.getLastChild() instanceof PsiJavaToken) &&
        !(replacement.getLastChild() instanceof PsiComment)
      ) {
      // replacement is expression (and pattern should be so)
      // assert ...
      replacement = ((PsiExpressionStatement)replacement).getExpression();
    }
    else if (replacement instanceof PsiDeclarationStatement &&
             ((PsiDeclarationStatement)replacement).getDeclaredElements().length == 1 &&
             (elementToReplace instanceof PsiVariable || elementToReplace instanceof PsiClass)) {
      return ((PsiDeclarationStatement)replacement).getDeclaredElements()[0];
    }
    else if (replacement instanceof PsiBlockStatement && elementToReplace instanceof PsiCodeBlock) {
      return ((PsiBlockStatement)replacement).getCodeBlock();
    }

    return replacement;
  }

  private boolean isSymbolReplacement(final PsiElement el) {
    return getSymbolReplacementTarget(el) != null;
  }

  /**
   * Copy all comments, doc comments, modifier lists and method bodies
   * that are present in matched nodes but not present in searched & replaced nodes
   */
  private void copyUnmatchedElements(PsiElement original, PsiElement replacement, ReplacementInfo info) {
    final Map<String, PsiElement> originalNamedElements = collectNamedElements(original);
    final Map<String, PsiElement> replacedNamedElements = collectNamedElements(replacement);

    if (originalNamedElements.isEmpty() && replacedNamedElements.isEmpty()) {
      Replacer.handleComments(original, replacement, info);
      return;
    }

    final Map<String, PsiElement> patternNamedElements = collectNamedElements(patternElements);

    for (String name : originalNamedElements.keySet()) {
      final PsiElement originalNamedElement = originalNamedElements.get(name);
      PsiElement replacementNamedElement = replacedNamedElements.get(name);
      final PsiElement patternNamedElement =
        ObjectUtils.coalesce(patternNamedElements.get(name), patternNamedElements.get('$' + info.getSearchPatternName(name) + '$'));
      if (patternNamedElement == null) continue;

      if (replacementNamedElement == null && originalNamedElements.size() == 1 && replacedNamedElements.size() == 1) {
        replacementNamedElement = replacedNamedElements.entrySet().iterator().next().getValue();
      }

      PsiElement comment = null;
      if (originalNamedElement instanceof PsiDocCommentOwner) {
        comment = ((PsiDocCommentOwner)originalNamedElement).getDocComment();
        if (comment == null) {
          PsiElement prevElement = originalNamedElement.getPrevSibling();
          if (prevElement instanceof PsiWhiteSpace) {
            prevElement = prevElement.getPrevSibling();
          }
          if (prevElement instanceof PsiComment) {
            comment = prevElement;
          }
        }
      }

      if (replacementNamedElement != null) {
        Replacer.handleComments(originalNamedElement, replacementNamedElement, info);
      }

      if (comment != null && replacementNamedElement instanceof PsiDocCommentOwner &&
          !(replacementNamedElement.getFirstChild() instanceof PsiDocComment)) {
        final PsiElement nextSibling = comment.getNextSibling();
        final PsiElement prevSibling = comment.getPrevSibling();
        replacementNamedElement.addRangeBefore(
          prevSibling instanceof PsiWhiteSpace ? prevSibling : comment,
          nextSibling instanceof PsiWhiteSpace ? nextSibling : comment,
          replacementNamedElement.getFirstChild()
        );
      }

      if (originalNamedElement instanceof PsiAnnotation &&
          patternNamedElement instanceof PsiAnnotation &&
          replacementNamedElement instanceof PsiAnnotation) {
        copyAnnotationParameters((PsiAnnotation)originalNamedElement,
                                 (PsiAnnotation)patternNamedElement,
                                 (PsiAnnotation)replacementNamedElement);
      }

      if (originalNamedElement instanceof PsiModifierListOwner &&
          patternNamedElement instanceof PsiModifierListOwner &&
          replacementNamedElement instanceof PsiModifierListOwner) {
        copyModifiersAndAnnotations((PsiModifierListOwner)originalNamedElement,
                                    (PsiModifierListOwner)patternNamedElement,
                                    (PsiModifierListOwner)replacementNamedElement);
      }

      if (originalNamedElement instanceof PsiMethod &&
          patternNamedElement instanceof PsiMethod &&
          replacementNamedElement instanceof PsiMethod) {
        copyMethodBodyIfNotReplaced((PsiMethod)originalNamedElement, (PsiMethod)patternNamedElement, (PsiMethod)replacementNamedElement);
      }

      if (originalNamedElement instanceof PsiVariable  &&
        patternNamedElement instanceof PsiVariable &&
        replacementNamedElement instanceof PsiVariable) {
        final PsiVariable originalVariable = (PsiVariable)originalNamedElement;
        final PsiVariable queryVariable = (PsiVariable)patternNamedElement;
        final PsiVariable replacementVariable = (PsiVariable)replacementNamedElement;
        if (originalVariable.hasInitializer() && !queryVariable.hasInitializer() && !replacementVariable.hasInitializer()) {
          replacementVariable.setInitializer(originalVariable.getInitializer());
        }
      }

      if (originalNamedElement instanceof PsiClass &&
          patternNamedElement instanceof PsiClass &&
          replacementNamedElement instanceof PsiClass) {
        final PsiClass originalClass = (PsiClass)originalNamedElement;
        final PsiClass queryClass = (PsiClass)patternNamedElement;
        final PsiClass replacementClass = (PsiClass)replacementNamedElement;

        copyClassType(originalClass, queryClass, replacementClass);
        copyExtendsListIfNotReplaced(originalClass, queryClass, replacementClass);
        copyImplementsListIfNotReplaced(originalClass, queryClass, replacementClass);
        copyTypeParameterListIfNotReplaced(originalClass, queryClass, replacementClass);

        copyUnmatchedMembers(originalClass, originalNamedElements, replacementClass);
      }
    }
  }

  private static void copyClassType(PsiClass originalClass, PsiClass patternClass, PsiClass replacementClass) {
    if (replacementClass.isEnum() || replacementClass.isAnnotationType() || replacementClass.isInterface()) {
      return;
    }
    if (originalClass.isEnum() && !patternClass.isEnum()) {
      transform(replacementClass, ClassType.ENUM);
    }
    else if (originalClass.isAnnotationType() && !patternClass.isAnnotationType()) {
      transform(replacementClass, ClassType.ANNOTATION);
    }
    else if (originalClass.isInterface() && !patternClass.isInterface()) {
      transform(replacementClass, ClassType.INTERFACE);
    }
  }

  enum ClassType {
    ENUM, INTERFACE, ANNOTATION
  }

  private static void transform(PsiClass replacementClass, ClassType type) {
    final PsiIdentifier nameIdentifier = replacementClass.getNameIdentifier();
    if (nameIdentifier == null) {
      return;
    }
    final PsiKeyword classKeyword = PsiTreeUtil.getPrevSiblingOfType(nameIdentifier, PsiKeyword.class);
    if (classKeyword == null) {
      return;
    }
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(replacementClass.getProject());
    final PsiClass aClass;
    switch (type) {
      case ANNOTATION:
        aClass = factory.createAnnotationType("X");
        break;
      case ENUM:
        aClass = factory.createEnum("X");
        break;
      case INTERFACE:
        aClass = factory.createInterface("X");
        break;
      default:
        throw new AssertionError();
    }
    final PsiIdentifier identifier = aClass.getNameIdentifier();
    final PsiKeyword newKeyword = PsiTreeUtil.getPrevSiblingOfType(identifier, PsiKeyword.class);
    assert newKeyword != null;
    final PsiElement replacement = classKeyword.replace(newKeyword);
    final PsiElement atToken = newKeyword.getPrevSibling();
    if (atToken != null) {
      replacementClass.addBefore(atToken, replacement);
    }
  }

  private static void copyUnmatchedMembers(PsiClass originalClass,
                                           Map<String, PsiElement> originalNamedElements,
                                           PsiClass replacementClass) {
    final List<? extends PsiElement> elements = originalClass.getUserData(GlobalMatchingVisitor.UNMATCHED_ELEMENTS_KEY);
    if (elements == null) {
      return;
    }
    final List<PsiNamedElement> anchors = PsiTreeUtil.getChildrenOfTypeAsList(replacementClass, PsiNamedElement.class);
    for (PsiNamedElement anchor : anchors) {
      final String replacedMemberName = anchor.getName();
      final PsiElement originalMember = originalNamedElements.get(replacedMemberName);
      if (originalMember == null) {
        continue;
      }
      final Iterator<? extends PsiElement> iterator = elements.iterator();
      while (iterator.hasNext()) {
        final PsiElement element = iterator.next();
        if (PsiElementOrderComparator.getInstance().compare(element, originalMember) < 0) {
          addElementAndWhitespaceBeforeAnchor(replacementClass, element, anchor);
          iterator.remove();
        }
        else {
          break;
        }
      }
    }
    final PsiElement anchor = replacementClass.getRBrace();
    if (anchor == null) {
      return;
    }
    for (PsiElement element : elements) {
      addElementAndWhitespaceBeforeAnchor(replacementClass, element, anchor);
    }
  }

  private static void addElementAndWhitespaceBeforeAnchor(PsiClass replacementClass, PsiElement element, PsiElement anchor) {
    final PsiElement replacementSibling = anchor.getPrevSibling();
    if (replacementSibling instanceof PsiWhiteSpace) {
      replacementSibling.delete();
    }
    final PsiElement prevSibling = element.getPrevSibling();
    if (prevSibling instanceof PsiWhiteSpace || PsiUtil.isJavaToken(prevSibling, JavaTokenType.COMMA)) {
      final PsiElement prevPrevSibling = prevSibling.getPrevSibling();
      if (PsiUtil.isJavaToken(prevPrevSibling, JavaTokenType.COMMA)) {
        replacementClass.addBefore(prevPrevSibling, anchor);
      }
      replacementClass.addBefore(prevSibling, anchor);
    }
    replacementClass.addBefore(element, anchor);
    final PsiElement nextSibling = element.getNextSibling();
    if (nextSibling instanceof PsiWhiteSpace) {
      replacementClass.addBefore(nextSibling, anchor);
    }
  }

  private static void copyMethodBodyIfNotReplaced(PsiMethod original, PsiMethod query, PsiMethod replacement) {
    final PsiCodeBlock originalBody = original.getBody();
    if (originalBody != null && query.getBody() == null && replacement.getBody() == null) {
      final PsiElement sibling = originalBody.getPrevSibling();
      if (sibling instanceof PsiWhiteSpace) {
        replacement.add(sibling);
      }
      replacement.add(originalBody);
    }
  }

  private void copyAnnotationParameters(PsiAnnotation original, PsiAnnotation query, PsiAnnotation replacement) {
    final PsiAnnotationParameterList originalParameters = original.getParameterList();
    if (originalParameters.getTextLength() > 0) {
      final PsiAnnotationParameterList replacementParameters = replacement.getParameterList();
      if (query.getParameterList().getTextLength() == 0 && replacementParameters.getTextLength() == 0) {
        replacementParameters.replace(originalParameters);
        return;
      }
      final List<? extends PsiElement> unmatchedAttributes = original.getUserData(GlobalMatchingVisitor.UNMATCHED_ELEMENTS_KEY);
      if (unmatchedAttributes != null) {
        for (PsiElement attribute : unmatchedAttributes) {
          replacementParameters.add(whiteSpace(attribute.getPrevSibling(), " "));
          replacementParameters.add(attribute);
        }
      }
    }
  }

  private void copyModifiersAndAnnotations(PsiModifierListOwner original, PsiModifierListOwner query, PsiModifierListOwner replacement) {
    final PsiModifierList originalModifierList = original.getModifierList();
    final PsiModifierList queryModifierList = query.getModifierList();
    final PsiModifierList replacementModifierList = replacement.getModifierList();

    if (originalModifierList == null || queryModifierList == null || replacementModifierList == null) {
      return;
    }
    if (queryModifierList.getTextLength() == 0 && replacementModifierList.getTextLength() == 0) {
      replacementModifierList.replace(originalModifierList);
      return;
    }
    final List<? extends PsiElement> unmatchedAnnotations = originalModifierList.getUserData(GlobalMatchingVisitor.UNMATCHED_ELEMENTS_KEY);
    final PsiElement anchor = replacementModifierList.getFirstChild();
    boolean append = anchor == null;
    PsiElement child = originalModifierList.getFirstChild();
    while (child != null) {
      if (child instanceof PsiKeyword) {
        append = true;
        @PsiModifier.ModifierConstant final String modifierText = child.getText();
        if (isCompatibleModifier(modifierText, replacementModifierList) && !queryModifierList.hasExplicitModifier(modifierText)) {
          if (anchor != null) replacementModifierList.add(whiteSpace(child.getPrevSibling(), " "));
          replacementModifierList.add(child);
        }
      }
      else if (child instanceof PsiAnnotation && (unmatchedAnnotations == null || unmatchedAnnotations.contains(child))) {
        if (append) {
          if (anchor != null) replacementModifierList.add(whiteSpace(child.getPrevSibling(), " "));
          replacementModifierList.add(child);
        }
        else {
          final PsiElement next = replacementModifierList.addBefore(child, anchor).getNextSibling();
          final PsiWhiteSpace whiteSpace = whiteSpace(child.getNextSibling(), " ");
          if (!(next instanceof PsiWhiteSpace)) {
            replacementModifierList.addBefore(whiteSpace, anchor);
          }
          else {
            next.replace(whiteSpace);
          }
        }
      }
      child = child.getNextSibling();
    }
  }

  private PsiWhiteSpace whiteSpace(PsiElement element, @SuppressWarnings("SameParameterValue") String defaultWs) {
    return element instanceof PsiWhiteSpace
           ? (PsiWhiteSpace)element
           : (PsiWhiteSpace)PsiParserFacade.SERVICE.getInstance(myProject).createWhiteSpaceFromText(defaultWs);
  }

  private static boolean isCompatibleModifier(String modifier, PsiModifierList modifierList) {
    if (PsiModifier.PUBLIC.equals(modifier) || PsiModifier.PROTECTED.equals(modifier) || PsiModifier.PRIVATE.equals(modifier)) {
      return !modifierList.hasExplicitModifier(PsiModifier.PUBLIC)
             && !modifierList.hasExplicitModifier(PsiModifier.PROTECTED)
             && !modifierList.hasExplicitModifier(PsiModifier.PRIVATE);
    }
    return true;
  }

  private PsiElement handleSymbolReplacement(PsiElement replacement, final PsiElement el) {
    final PsiNamedElement nameElement = getSymbolReplacementTarget(el);
    if (nameElement != null) {
      final PsiElement oldReplacement = replacement;
      replacement = el.copy();
      ((PsiNamedElement)replacement).setName(oldReplacement.getText());
    }

    return replacement;
  }

  @Override
  public void replace(final @NotNull ReplacementInfo info, @NotNull ReplaceOptions options) {
    final PsiElement elementToReplace = StructuralSearchUtil.getPresentableElement(info.getMatch(0));
    if (elementToReplace == null) {
      return;
    }
    final PsiElement elementParent = elementToReplace.getParent();
    String replacementToMake = info.getReplacement();
    final boolean listContext = isListContext(elementToReplace);

    if (elementToReplace instanceof PsiAnnotation && !replacementToMake.isEmpty() &&
        !StringUtil.startsWithChar(replacementToMake, '@')) {
      replacementToMake = "@" + replacementToMake;
    }

    final LanguageFileType fileType = myReplaceOptions.getMatchOptions().getFileType();
    assert fileType != null;
    final PsiElement[] replacements = MatcherImplUtil.createTreeFromText(
      replacementToMake,
      elementToReplace instanceof PsiMember && !isSymbolReplacement(elementToReplace) ?
      PatternTreeContext.Class :
      PatternTreeContext.Block,
      fileType,
      myProject);

    if (elementToReplace instanceof PsiAnnotation && replacements.length == 1) {
      final PsiElement replacement = replacements[0];
      if (replacement instanceof PsiDeclarationStatement) {
        final PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement)replacement;
        final PsiElement firstChild = declarationStatement.getFirstChild();
        if (firstChild instanceof PsiModifierList) {
          copyUnmatchedElements(elementToReplace, replacement, info);
          final PsiModifierList modifierList = (PsiModifierList)firstChild;
          for (PsiElement child : modifierList.getChildren()) {
            elementParent.addBefore(child, elementToReplace);
          }
        }
      }
      elementToReplace.delete();
      return;
    }
    if (listContext) {
      if (replacements.length > 1) {
        final PsiElement replacement = elementParent.addRangeBefore(replacements[0], replacements[replacements.length - 1], elementToReplace);
        copyUnmatchedElements(elementToReplace, replacement, info);
      }
      else if (replacements.length == 1) {
        PsiElement replacement = getMatchExpr(replacements[0], elementToReplace);
        if (elementToReplace instanceof PsiParameter && replacement instanceof PsiLocalVariable) {
          final PsiVariable variable = (PsiVariable)replacement;
          final PsiIdentifier identifier = variable.getNameIdentifier();
          assert identifier != null;
          final String text = variable.getText();

          // chop off unneeded semicolons & initializers
          final String parameterText = text.substring(0, identifier.getStartOffsetInParent() + identifier.getTextLength());
          replacement = JavaPsiFacade.getElementFactory(variable.getProject()).createParameterFromText(parameterText, variable);
        }
        final PsiElement patternElement = patternElements[0];
        if (elementToReplace instanceof PsiAnonymousClass &&
            patternElement instanceof PsiClass && !(patternElement instanceof PsiAnonymousClass) &&
            replacement instanceof PsiClass && !(replacement instanceof PsiAnonymousClass)) {
          final PsiAnonymousClass anonymousClass = (PsiAnonymousClass)elementToReplace;
          final PsiClass replacementClass = (PsiClass)replacement;
          final PsiExpressionList argumentList = anonymousClass.getArgumentList();
          final PsiElement brace = replacementClass.getLBrace();
          assert brace != null;

          String typeParametersText = "";
          if (!replacementClass.hasTypeParameters()) {
            if (!((PsiClass)patternElement).hasTypeParameters()) {
              final PsiReferenceParameterList parameterList = anonymousClass.getBaseClassReference().getParameterList();
              if (parameterList != null) {
                typeParametersText = parameterList.getText();
              }
            }
          }
          else {
            final PsiTypeParameterList parameterList = replacementClass.getTypeParameterList();
            if (parameterList != null) {
              typeParametersText = parameterList.getText();
            }
          }

          final PsiElementFactory factory = JavaPsiFacade.getElementFactory(elementToReplace.getProject());
          final PsiNewExpression newExpression = (PsiNewExpression)
            factory.createExpressionFromText("new " + replacementClass.getName() + typeParametersText +
                                             (argumentList == null ? "()" : argumentList.getText()) +
                                             replacementClass.getText().substring(brace.getStartOffsetInParent()),
                                             elementToReplace);
          replacement = newExpression.getAnonymousClass();
          assert replacement != null;
        }

        copyUnmatchedElements(elementToReplace, replacement, info);
        replacement = handleSymbolReplacement(replacement, elementToReplace);

        if (replacement instanceof PsiTryStatement) {
          final PsiTryStatement tryStatement = (PsiTryStatement)replacement;
          final List<? extends PsiElement> unmatchedElements = elementToReplace.getUserData(GlobalMatchingVisitor.UNMATCHED_ELEMENTS_KEY);
          if (unmatchedElements != null) {
            final PsiElement firstElement = unmatchedElements.get(0);
            if (firstElement instanceof PsiResourceList) addElementAfterAnchor(tryStatement, firstElement, tryStatement.getFirstChild());
            outer:
            for (final PsiElement element : unmatchedElements) {
              if (element instanceof PsiCatchSection) {
                final PsiCatchSection[] catches = tryStatement.getCatchSections();
                final PsiCatchSection catchSection = (PsiCatchSection)element;
                if (catches.length == 0) {
                  addElementAfterAnchor(tryStatement, element, tryStatement.getTryBlock());
                }
                else {
                  final PsiType newType = catchSection.getCatchType();
                  for (PsiCatchSection existingCatch : catches) {
                    final PsiType existingType = existingCatch.getCatchType();
                    if (existingType != null && newType != null) {
                      if (existingType.isAssignableFrom(newType)) {
                        addElementBeforeAnchor(tryStatement, element, existingCatch);
                        continue outer;
                      }
                    }
                  }
                  addElementAfterAnchor(tryStatement, element, catches[catches.length - 1]);
                }
              }
            }
            final PsiElement lastElement = unmatchedElements.get(unmatchedElements.size() - 1);
            if (lastElement instanceof PsiCodeBlock) {
              final PsiElement finallyKeyword = PsiTreeUtil.skipWhitespacesBackward(lastElement);
              assert finallyKeyword != null;
              final PsiElement finallyAnchor = tryStatement.getLastChild();
              addElementAfterAnchor(tryStatement, lastElement, finallyAnchor);
              addElementAfterAnchor(tryStatement, finallyKeyword, finallyAnchor);
            }
          }
        }

        try {
          final PsiElement inserted = elementParent.addBefore(replacement, elementToReplace);

          if (replacement instanceof PsiComment && (elementParent instanceof PsiIfStatement || elementParent instanceof PsiLoopStatement)) {
            elementParent.addAfter(createSemicolon(replacement), inserted);
          }
        }
        catch (IncorrectOperationException e) {
          elementToReplace.replace(replacement);
        }
      }
    }
    else if (replacements.length > 0) {
      PsiElement replacement = ReplacerUtil.copySpacesAndCommentsBefore(elementToReplace, replacements, replacementToMake, elementParent);

      replacement = getMatchExpr(replacement, elementToReplace);

      if (replacement instanceof PsiStatement &&
          !(replacement.getLastChild() instanceof PsiJavaToken) &&
          !(replacement.getLastChild() instanceof PsiComment)
        ) {
        // assert w/o ;
        final PsiElement prevLastChildInParent = replacement.getLastChild().getPrevSibling();

        if (prevLastChildInParent != null) {
          elementParent.addRangeBefore(replacement.getFirstChild(), prevLastChildInParent, elementToReplace);
        }
        else {
          elementParent.addBefore(replacement.getFirstChild(), elementToReplace);
        }

        elementToReplace.getNode().getTreeParent().removeChild(elementToReplace.getNode());
      }
      else {
        copyUnmatchedElements(elementToReplace, replacement, info);

        replacement = handleSymbolReplacement(replacement, elementToReplace);
        elementToReplace.replace(replacement);
      }
    }
    else {
      final PsiElement nextSibling = elementToReplace.getNextSibling();
      elementToReplace.delete();
      if (nextSibling instanceof PsiWhiteSpace && nextSibling.isValid()) {
        nextSibling.delete();
      }
    }

    if (listContext) {
      final int matchSize = info.getMatchesCount();

      for (int i = 0; i < matchSize; ++i) {
        final PsiElement matchElement = info.getMatch(i);
        final PsiElement element = StructuralSearchUtil.getPresentableElement(matchElement);

        if (element == null) continue;
        PsiElement firstToDelete = element;
        PsiElement lastToDelete = element;
        PsiElement prevSibling = element.getPrevSibling();
        PsiElement nextSibling = element.getNextSibling();

        if (prevSibling instanceof PsiWhiteSpace) {
          firstToDelete = prevSibling;
          prevSibling = prevSibling.getPrevSibling();
        }
        else if (prevSibling == null && nextSibling instanceof PsiWhiteSpace) {
          lastToDelete = nextSibling;
        }

        if (element instanceof PsiExpression) {
          final PsiElement parent = element.getParent().getParent();
          if ((parent instanceof PsiCall || parent instanceof PsiAnonymousClass) && PsiUtil.isJavaToken(prevSibling, JavaTokenType.COMMA)) {
            firstToDelete = prevSibling;
          }
        }
        else if (element instanceof PsiParameter && PsiUtil.isJavaToken(prevSibling, JavaTokenType.COMMA)) {
          firstToDelete = prevSibling;
        }
        else if (element instanceof PsiField) {
          while (PsiUtil.isJavaToken(nextSibling, JavaTokenType.COMMA)) {
            lastToDelete = PsiTreeUtil.skipWhitespacesForward(nextSibling);
            nextSibling = PsiTreeUtil.skipWhitespacesForward(lastToDelete);
          }
        }

        element.getParent().deleteChildRange(firstToDelete, lastToDelete);
      }
    }
  }

  private static void copyTypeParameterListIfNotReplaced(PsiClass original, PsiClass query, PsiClass replacement) {
    final PsiTypeParameterList originalTypeParameterList = original.getTypeParameterList();
    final PsiTypeParameterList queryTypeParameterList = query.getTypeParameterList();
    final PsiTypeParameterList replacementTypeParameterList = replacement.getTypeParameterList();
    if (originalTypeParameterList == null || queryTypeParameterList == null || replacementTypeParameterList == null) {
      return;
    }
    if (originalTypeParameterList.getTypeParameters().length != 0 &&
        queryTypeParameterList.getTypeParameters().length == 0 &&
        replacementTypeParameterList.getTypeParameters().length == 0) {
      replacementTypeParameterList.replace(originalTypeParameterList);
    }
  }

  private static void copyImplementsListIfNotReplaced(PsiClass original, PsiClass query, PsiClass replacement) {
    copyReferenceListIfNotReplaced(original.getImplementsList(), query.getImplementsList(), replacement.getImplementsList());
  }

  private static void copyExtendsListIfNotReplaced(PsiClass original, PsiClass query, PsiClass replacement) {
    copyReferenceListIfNotReplaced(original.getExtendsList(), query.getExtendsList(), replacement.getExtendsList());
  }

  private static void copyReferenceListIfNotReplaced(PsiReferenceList originalReferenceList,
                                                     PsiReferenceList queryReferenceList,
                                                     PsiReferenceList replacementReferenceList) {
    if (originalReferenceList == null || queryReferenceList == null || replacementReferenceList == null) {
      return;
    }
    if (originalReferenceList.getReferenceElements().length != 0 &&
        queryReferenceList.getReferenceElements().length == 0 &&
        replacementReferenceList.getReferenceElements().length == 0) {
      replacementReferenceList.replace(originalReferenceList);
    }
  }

  private static void addElementAfterAnchor(PsiElement parentElement, PsiElement element, PsiElement anchor) {
    parentElement.addAfter(element, anchor);
    final PsiElement sibling = element.getPrevSibling();
    if (sibling instanceof PsiWhiteSpace) parentElement.addAfter(sibling, anchor); // recycle whitespace
  }

  private static void addElementBeforeAnchor(PsiElement parentElement, PsiElement element, PsiElement anchor) {
    final PsiElement sibling = element.getPrevSibling().copy();
    parentElement.addBefore(element, anchor);
    if (sibling instanceof PsiWhiteSpace) parentElement.addBefore(sibling, anchor);
  }

  @Override
  public void postProcess(@NotNull PsiElement affectedElement, @NotNull ReplaceOptions options) {
    if (!affectedElement.isValid()) {
      return;
    }
    if (myReplaceOptions.isToUseStaticImport()) {
      shortenWithStaticImports(affectedElement, 0, affectedElement.getTextLength());
    }
    if (myReplaceOptions.isToShortenFQN()) {
      final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(affectedElement.getProject());
      codeStyleManager.shortenClassReferences(affectedElement, 0, affectedElement.getTextLength());
    }
  }

  private static void shortenWithStaticImports(PsiElement affectedElement, int startOffset, int endOffset) {
    final int elementOffset = affectedElement.getTextOffset();
    final int finalStartOffset = startOffset + elementOffset;
    final int finalEndOffset = endOffset + elementOffset;
    final List<PsiJavaCodeReferenceElement> references = new SmartList<>();
    final JavaRecursiveElementVisitor collector = new JavaRecursiveElementVisitor() {
      @Override
      public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
        final int offset = reference.getTextOffset();
        if (offset > finalEndOffset) {
          return;
        }
        super.visitReferenceElement(reference);
        if (offset + reference.getTextLength() < finalStartOffset) {
          return;
        }
        if (reference.getTypeParameters().length != 0 || reference instanceof PsiMethodReferenceExpression) {
          return;
        }
        final PsiElement target = reference.resolve();
        if (!(target instanceof PsiMember)) {
          return;
        }
        final PsiMember member = (PsiMember)target;
        if (!member.hasModifierProperty(PsiModifier.STATIC)) {
          return;
        }
        if (reference.getQualifier() == null) {
          return;
        }
        references.add(reference);
      }
    };
    affectedElement.accept(collector);
    for (PsiJavaCodeReferenceElement expression : references) {
      final PsiElement target = expression.resolve();
      if (!(target instanceof PsiMember)) {
        continue;
      }
      final PsiMember member = (PsiMember)target;
      final PsiClass containingClass = member.getContainingClass();
      if (containingClass == null) {
        continue;
      }
      final String className = containingClass.getQualifiedName();
      if (className == null) {
        continue;
      }
      final String name = member.getName();
      if (name == null) {
        continue;
      }
      if (ImportUtils.addStaticImport(className, name, expression)) {
        final PsiElement qualifier = expression.getQualifier();
        if (qualifier != null) {
          qualifier.delete();
        }
      }
    }
  }

  private static PsiElement createSemicolon(final PsiElement space) {
    return JavaPsiFacade.getElementFactory(space.getProject()).createStatementFromText(";", null).getFirstChild();
  }

  public static Map<String, PsiElement> collectNamedElements(PsiElement... elements) {
    final Collector collector = new Collector();
    for (PsiElement element : elements) {
      element.accept(collector);
    }
    return collector.namedElements;
  }

  private static class Collector extends JavaRecursiveElementWalkingVisitor {
    private final HashMap<String, PsiElement> namedElements = new HashMap<>(1); // uses null keys

    @Override
    public void visitClass(PsiClass aClass) {
      if (aClass instanceof PsiAnonymousClass) {
        final PsiAnonymousClass anonymousClass = (PsiAnonymousClass)aClass;
        final String name = anonymousClass.getBaseClassReference().getReferenceName();
        if (!namedElements.containsKey(name)) {
          namedElements.put(name, aClass);
        }
      }
      else {
        handleNamedElement(aClass);
      }
    }

    private void handleNamedElement(final PsiNamedElement named) {
      final String name = named.getName();

      if (!namedElements.containsKey(name)) {
        namedElements.put(name, named);
        named.acceptChildren(this);
      }
    }

    @Override
    public void visitVariable(PsiVariable var) {
      handleNamedElement(var);
    }

    @Override
    public void visitMethod(PsiMethod method) {
      handleNamedElement(method);
    }

    @Override
    public void visitAnnotation(PsiAnnotation annotation) {
      final PsiJavaCodeReferenceElement referenceElement = annotation.getNameReferenceElement();
      if (referenceElement != null) {
        final String name = referenceElement.getText();
        if (!namedElements.containsKey(name)) {
          namedElements.put(name, annotation);
          super.visitAnnotation(annotation);
        }
      }
    }
  }
}

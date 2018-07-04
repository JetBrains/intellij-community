// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch;

import com.intellij.codeInsight.AnnotationUtil;
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
import com.intellij.structuralsearch.plugin.replace.impl.ReplacementContext;
import com.intellij.structuralsearch.plugin.replace.impl.Replacer;
import com.intellij.structuralsearch.plugin.replace.impl.ReplacerUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.siyeh.ig.psiutils.ImportUtils;
import com.siyeh.ig.psiutils.PsiElementOrderComparator;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * @author Eugene.Kudelevsky
 */
public class JavaReplaceHandler extends StructuralReplaceHandler {
  private final ReplacementContext myContext;
  private final PsiElement[] patternElements;

  public JavaReplaceHandler(ReplacementContext context) {
    this.myContext = context;
    patternElements = MatcherImplUtil.createTreeFromText(
      myContext.getOptions().getMatchOptions().getSearchPattern(),
      PatternTreeContext.Block,
      myContext.getOptions().getMatchOptions().getFileType(),
      myContext.getProject()
    );
  }

  private static PsiElement findRealSubstitutionElement(PsiElement el) {
    if (el instanceof PsiIdentifier) {
      // matches are tokens, identifiers, etc
      el = el.getParent();
    }

    if (el instanceof PsiReferenceExpression &&
        el.getParent() instanceof PsiMethodCallExpression
      ) {
      // method
      el = el.getParent();
    }

    if (el instanceof PsiDeclarationStatement && ((PsiDeclarationStatement)el).getDeclaredElements()[0] instanceof PsiClass) {
      el = ((PsiDeclarationStatement)el).getDeclaredElements()[0];
    }
    return el;
  }

  private static boolean isListContext(PsiElement el) {
    if (el instanceof PsiParameter) {
      return true;
    }
    final PsiElement parent = el.getParent();

    return (parent instanceof PsiExpressionList && !parent.getClass().getSimpleName().startsWith("Jsp")) ||
           parent instanceof PsiCodeBlock ||
           parent instanceof PsiClass ||
           parent instanceof PsiIfStatement && (((PsiIfStatement)parent).getThenBranch() == el ||
                                                ((PsiIfStatement)parent).getElseBranch() == el) ||
           parent instanceof PsiLoopStatement && ((PsiLoopStatement)parent).getBody() == el;
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
    Map<String, PsiNamedElement> originalNamedElements = Collector.collectNamedElements(original);
    Map<String, PsiNamedElement> replacedNamedElements = Collector.collectNamedElements(replacement);

    if (originalNamedElements.isEmpty() && replacedNamedElements.isEmpty()) {
      Replacer.handleComments(original, replacement, info);
      return;
    }

    Map<String, PsiNamedElement> patternNamedElements = Collector.collectNamedElements(patternElements);

    for (String name : originalNamedElements.keySet()) {
      PsiNamedElement originalNamedElement = originalNamedElements.get(name);
      PsiNamedElement replacementNamedElement = replacedNamedElements.get(name);
      String key = ObjectUtils.notNull(info.getSearchPatternName(name), name);
      PsiNamedElement patternNamedElement = patternNamedElements.get(key);

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

      if (replacementNamedElement != null && patternNamedElement != null) {
        Replacer.handleComments(originalNamedElement, replacementNamedElement, info);
      }

      if (comment != null && replacementNamedElement instanceof PsiDocCommentOwner &&
          !(replacementNamedElement.getFirstChild() instanceof PsiDocComment)) {
        final PsiElement nextSibling = comment.getNextSibling();
        PsiElement prevSibling = comment.getPrevSibling();
        replacementNamedElement.addRangeBefore(
          prevSibling instanceof PsiWhiteSpace ? prevSibling : comment,
          nextSibling instanceof PsiWhiteSpace ? nextSibling : comment,
          replacementNamedElement.getFirstChild()
        );
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

        copyExtendsListIfNotReplaced(originalClass, queryClass, replacementClass);
        copyImplementsListIfNotReplaced(originalClass, queryClass, replacementClass);
        copyTypeParameterListIfNotReplaced(originalClass, queryClass, replacementClass);

        copyUnmatchedMembers(originalClass, originalNamedElements, replacementClass);
      }
    }
  }

  private static void copyUnmatchedMembers(PsiClass originalClass,
                                           Map<String, PsiNamedElement> originalNamedElements,
                                           PsiClass replacementClass) {
    final List<? extends PsiElement> elements = originalClass.getUserData(GlobalMatchingVisitor.UNMATCHED_ELEMENTS_KEY);
    if (elements == null) {
      return;
    }
    final List<PsiNamedElement> anchors = PsiTreeUtil.getChildrenOfTypeAsList(replacementClass, PsiNamedElement.class);
    for (PsiNamedElement anchor : anchors) {
      final String replacedMemberName = anchor.getName();
      final PsiNamedElement originalMember = originalNamedElements.get(replacedMemberName);
      if (originalMember == null) {
        continue;
      }
      for (Iterator<? extends PsiElement> iterator = elements.iterator(); iterator.hasNext(); ) {
        PsiElement element = iterator.next();
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

  private static void copyModifiersAndAnnotations(PsiModifierListOwner original,
                                                  PsiModifierListOwner query,
                                                  PsiModifierListOwner replacement) {
    final PsiModifierList originalModifierList = original.getModifierList();
    final PsiModifierList queryModifierList = query.getModifierList();
    final PsiModifierList replacementModifierList = replacement.getModifierList();

    if (originalModifierList == null || queryModifierList == null || replacementModifierList == null) {
      return;
    }
    if (originalModifierList.getTextLength() != 0) {
      final PsiModifierList copy = (PsiModifierList)originalModifierList.copy();
      for (String modifier : PsiModifier.MODIFIERS) {
        if (replacementModifierList.hasExplicitModifier(modifier)) {
          copy.setModifierProperty(modifier, true);
        }
        else if (queryModifierList.hasExplicitModifier(modifier) && !replacementModifierList.hasModifierProperty(modifier)) {
          copy.setModifierProperty(modifier, false);
        }
      }
      final List<? extends PsiElement> unmatchedAnnotations = originalModifierList.getUserData(GlobalMatchingVisitor.UNMATCHED_ELEMENTS_KEY);
      if (unmatchedAnnotations != null) {
        outer:
        for (PsiAnnotation copyAnnotation : copy.getAnnotations()) {
          for (PsiElement unmatchedAnnotation : unmatchedAnnotations) {
            if (AnnotationUtil.equal(copyAnnotation, (PsiAnnotation)unmatchedAnnotation)) {
              continue outer;
            }
          }
          copyAnnotation.delete();
        }
      }
      for (PsiAnnotation annotation : replacementModifierList.getAnnotations()) {
        copy.addBefore(annotation, copy.getFirstChild());
      }
      replacementModifierList.replace(copy);
    }
  }

  private PsiElement handleSymbolReplacement(PsiElement replacement, final PsiElement el) {
    PsiNamedElement nameElement = getSymbolReplacementTarget(el);
    if (nameElement != null) {
      PsiElement oldReplacement = replacement;
      replacement = el.copy();
      ((PsiNamedElement)replacement).setName(oldReplacement.getText());
    }

    return replacement;
  }

  @Override
  public void replace(final ReplacementInfo info, ReplaceOptions options) {
    PsiElement elementToReplace = info.getMatch(0);
    if (elementToReplace == null) {
      return;
    }
    elementToReplace = findRealSubstitutionElement(elementToReplace);
    final PsiElement elementParent = elementToReplace.getParent();
    String replacementToMake = info.getReplacement();
    final boolean listContext = isListContext(elementToReplace);

    if (elementToReplace instanceof PsiAnnotation && !replacementToMake.isEmpty() &&
        !StringUtil.startsWithChar(replacementToMake, '@')) {
      replacementToMake = "@" + replacementToMake;
    }

    final PsiElement[] replacements = ReplacerUtil
      .createTreeForReplacement(replacementToMake, elementToReplace instanceof PsiMember && !isSymbolReplacement(elementToReplace) ?
                                                   PatternTreeContext.Class :
                                                   PatternTreeContext.Block, myContext);

    if (elementToReplace instanceof PsiAnnotation && replacements.length == 1) {
      final PsiElement replacement = replacements[0];
      if (replacement instanceof PsiDeclarationStatement) {
        final PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement)replacement;
        final PsiElement firstChild = declarationStatement.getFirstChild();
        if (firstChild instanceof PsiModifierList) {
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

        copyUnmatchedElements(elementToReplace, replacement, info);
        replacement = handleSymbolReplacement(replacement, elementToReplace);

        if (replacement instanceof PsiTryStatement) {
          final PsiTryStatement tryStatement = (PsiTryStatement)replacement;
          final List<? extends PsiElement> unmatchedElements = elementToReplace.getUserData(GlobalMatchingVisitor.UNMATCHED_ELEMENTS_KEY);
          if (unmatchedElements != null) {
            final PsiElement firstElement = unmatchedElements.get(0);
            if (firstElement instanceof PsiResourceList) addElementAfterAnchor(tryStatement, firstElement, tryStatement.getFirstChild());
            final PsiCatchSection[] catches = tryStatement.getCatchSections();
            final PsiElement anchor = catches.length == 0 ? tryStatement.getTryBlock() : catches[catches.length - 1];
            for (int i = unmatchedElements.size() - 1; i >= 0; i--) {
              final PsiElement element = unmatchedElements.get(i);
              if ((element instanceof PsiCatchSection)) addElementAfterAnchor(tryStatement, element, anchor);
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

          if (replacement instanceof PsiComment &&
              (elementParent instanceof PsiIfStatement ||
               elementParent instanceof PsiLoopStatement
              )
            ) {
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
        PsiElement matchElement = info.getMatch(i);
        PsiElement element = findRealSubstitutionElement(matchElement);

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

  @Override
  public void postProcess(PsiElement affectedElement, ReplaceOptions options) {
    if (!affectedElement.isValid()) {
      return;
    }
    if (options.isToUseStaticImport()) {
      shortenWithStaticImports(affectedElement, 0, affectedElement.getTextLength());
    }
    if (options.isToShortenFQN()) {
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
    return JavaPsiFacade.getInstance(space.getProject()).getElementFactory().createStatementFromText(";", null).getFirstChild();
  }

  private static class Collector extends JavaRecursiveElementWalkingVisitor {
    private final HashMap<String, PsiNamedElement> namedElements = new HashMap<>(1);

    public static  Map<String, PsiNamedElement> collectNamedElements(PsiElement... elements) {
      final Collector collector = new Collector();
      for (PsiElement element : elements) {
        element.accept(collector);
      }
      return collector.namedElements;
    }

    @Override
    public void visitClass(PsiClass aClass) {
      if (aClass instanceof PsiAnonymousClass) return;
      handleNamedElement(aClass);
    }

    private void handleNamedElement(final PsiNamedElement named) {
      String name = named.getName();

      assert name != null;

      if (StructuralSearchUtil.isTypedVariable(name)) {
        name = name.substring(1, name.length() - 1);
      }

      if (!namedElements.containsKey(name)) namedElements.put(name, named);
      named.acceptChildren(this);
    }

    @Override
    public void visitVariable(PsiVariable var) {
      handleNamedElement(var);
    }

    @Override
    public void visitMethod(PsiMethod method) {
      handleNamedElement(method);
    }
  }
}

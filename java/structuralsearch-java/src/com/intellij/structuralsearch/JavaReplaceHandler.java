package com.intellij.structuralsearch;

import com.intellij.openapi.fileTypes.StdFileTypes;
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
import com.siyeh.ig.psiutils.ImportUtils;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Eugene.Kudelevsky
 */
public class JavaReplaceHandler extends StructuralReplaceHandler {
  private final ReplacementContext myContext;
  private PsiCodeBlock codeBlock;

  public JavaReplaceHandler(ReplacementContext context) {
    this.myContext = context;
  }

  private PsiCodeBlock getCodeBlock() throws IncorrectOperationException {
    if (codeBlock == null) {
      codeBlock = (PsiCodeBlock)MatcherImplUtil.createTreeFromText(
        myContext.getOptions().getMatchOptions().getSearchPattern(),
        PatternTreeContext.Block,
        myContext.getOptions().getMatchOptions().getFileType(),
        myContext.getProject()
      )[0].getParent();
    }
    return codeBlock;
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
    final PsiElement parent = el.getParent();

    return parent instanceof PsiParameterList ||
           parent instanceof PsiExpressionList ||
           parent instanceof PsiCodeBlock ||
           parent instanceof PsiClass ||
           parent instanceof PsiIfStatement && (((PsiIfStatement)parent).getThenBranch() == el ||
                                                ((PsiIfStatement)parent).getElseBranch() == el) ||
           parent instanceof PsiLoopStatement && ((PsiLoopStatement)parent).getBody() == el;
  }

  @Nullable
  private PsiNamedElement getSymbolReplacementTarget(final PsiElement el)
    throws IncorrectOperationException {
    if (myContext.getOptions().getMatchOptions().getFileType() != StdFileTypes.JAVA) return null; //?
    final PsiStatement[] searchStatements = getCodeBlock().getStatements();
    if (searchStatements.length > 0 &&
        searchStatements[0] instanceof PsiExpressionStatement) {
      final PsiExpression expression = ((PsiExpressionStatement)searchStatements[0]).getExpression();

      if (expression instanceof PsiReferenceExpression &&
          ((PsiReferenceExpression)expression).getQualifierExpression() == null
        ) {
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

  private boolean isSymbolReplacement(final PsiElement el) throws IncorrectOperationException {
    return getSymbolReplacementTarget(el) != null;
  }

  /**
   * Copy all comments, doc comments, modifier lists and method bodies
   * that are present in matched nodes but not present in searched & replaced nodes
   */
  private void copyUnmatchedElements(final PsiElement original, final PsiElement replacement) {

    Map<String, String> newNameToSearchPatternNameMap = myContext.getNewName2PatternNameMap();

    Map<String, PsiNamedElement> originalNamedElements = Collector.collectNamedElements(original);
    Map<String, PsiNamedElement> replacedNamedElements = Collector.collectNamedElements(replacement);

    if (originalNamedElements.size() == 0 && replacedNamedElements.size() == 0) {
      Replacer.handleComments(original, replacement, myContext);
      return;
    }

    Map<String, PsiNamedElement> searchedNamedElements = Collector.collectNamedElements(getCodeBlock());

    for (String name : originalNamedElements.keySet()) {
      PsiNamedElement originalNamedElement = originalNamedElements.get(name);
      PsiNamedElement replacementNamedElement = replacedNamedElements.get(name);
      String key = newNameToSearchPatternNameMap.get(name);
      if (key == null) key = name;
      PsiNamedElement searchedNamedElement = searchedNamedElements.get(key);

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

      if (replacementNamedElement != null && searchedNamedElement != null) {
        Replacer.handleComments(originalNamedElement, replacementNamedElement, myContext);
      }

      if (comment != null && replacementNamedElement instanceof PsiDocCommentOwner &&
          !(replacementNamedElement.getFirstChild() instanceof PsiDocComment)
        ) {
        final PsiElement nextSibling = comment.getNextSibling();
        PsiElement prevSibling = comment.getPrevSibling();
        replacementNamedElement.addRangeBefore(
          prevSibling instanceof PsiWhiteSpace ? prevSibling : comment,
          nextSibling instanceof PsiWhiteSpace ? nextSibling : comment,
          replacementNamedElement.getFirstChild()
        );
      }

      if (originalNamedElement instanceof PsiModifierListOwner &&
          replacementNamedElement instanceof PsiModifierListOwner
        ) {
        PsiModifierList modifierList = ((PsiModifierListOwner)originalNamedElements.get(name)).getModifierList();

        if (searchedNamedElement instanceof PsiModifierListOwner) {
          PsiModifierList modifierListOfSearchedElement = ((PsiModifierListOwner)searchedNamedElement).getModifierList();
          final PsiModifierListOwner modifierListOwner = ((PsiModifierListOwner)replacementNamedElement);
          PsiModifierList modifierListOfReplacement = modifierListOwner.getModifierList();

          if (modifierListOfSearchedElement.getTextLength() == 0 &&
              modifierListOfReplacement.getTextLength() == 0 &&
              modifierList.getTextLength() > 0) {
            modifierListOfReplacement.replace(modifierList);
          } else if (modifierListOfSearchedElement.getTextLength() == 0 && modifierList.getTextLength() > 0) {
            final PsiModifierList copy = (PsiModifierList)modifierList.copy();
            for (String modifier : PsiModifier.MODIFIERS) {
              if (modifierListOfReplacement.hasExplicitModifier(modifier)) {
                copy.setModifierProperty(modifier, true);
              }
            }
            final PsiElement anchor = copy.getFirstChild();
            for (PsiAnnotation annotation : modifierListOfReplacement.getAnnotations()) {
              copy.addBefore(annotation, anchor);
            }
            modifierListOfReplacement.replace(copy);
          }
        }
      }

      if (originalNamedElement instanceof PsiMethod &&
          searchedNamedElement instanceof PsiMethod &&
          replacementNamedElement instanceof PsiMethod) {
        final PsiMethod searchedMethod = (PsiMethod)searchedNamedElement;
        final PsiMethod replacementMethod = (PsiMethod)replacementNamedElement;
        if (searchedMethod.getBody() == null && replacementMethod.getBody() == null) {
          final PsiMethod originalMethod = (PsiMethod)originalNamedElement;
          final PsiCodeBlock originalBody = originalMethod.getBody();
          if (originalBody != null) {
            replacementMethod.add(originalBody);
          }
        }
      }
    }
  }

  private PsiElement handleSymbolReplacement(PsiElement replacement, final PsiElement el) throws IncorrectOperationException {
    PsiNamedElement nameElement = getSymbolReplacementTarget(el);
    if (nameElement != null) {
      PsiElement oldReplacement = replacement;
      replacement = el.copy();
      ((PsiNamedElement)replacement).setName(oldReplacement.getText());
    }

    return replacement;
  }

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

    final PsiElement[] statements = ReplacerUtil
      .createTreeForReplacement(replacementToMake, elementToReplace instanceof PsiMember && !isSymbolReplacement(elementToReplace) ?
                                                   PatternTreeContext.Class :
                                                   PatternTreeContext.Block, myContext);

    if (elementToReplace instanceof PsiAnnotation && statements.length == 1) {
      final PsiElement statement = statements[0];
      if (statement instanceof PsiDeclarationStatement) {
        final PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement)statement;
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
      if (statements.length > 1) {
        final PsiElement replacement = elementParent.addRangeBefore(statements[0], statements[statements.length - 1], elementToReplace);
        copyUnmatchedElements(elementToReplace, replacement);
      }
      else if (statements.length == 1) {
        PsiElement replacement = getMatchExpr(statements[0], elementToReplace);
        if (elementToReplace instanceof PsiParameter && replacement instanceof PsiLocalVariable) {
          final PsiVariable variable = (PsiVariable)replacement;
          final PsiIdentifier identifier = variable.getNameIdentifier();
          assert identifier != null;
          final String text = variable.getText();

          // chop off unneeded semicolons & initializers
          final String parameterText = text.substring(0, identifier.getStartOffsetInParent() + identifier.getTextLength());
          replacement = JavaPsiFacade.getElementFactory(variable.getProject()).createParameterFromText(parameterText, variable);
        }

        copyUnmatchedElements(elementToReplace, replacement);
        replacement = handleSymbolReplacement(replacement, elementToReplace);

        if (replacement instanceof PsiTryStatement) {
          final PsiTryStatement tryStatement = (PsiTryStatement)replacement;
          final List<PsiElement> unmatchedElements = elementToReplace.getUserData(GlobalMatchingVisitor.UNMATCHED_ELEMENTS_KEY);
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
              final PsiElement finallyKeyword = PsiTreeUtil.skipSiblingsBackward(lastElement, PsiWhiteSpace.class);
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
    else if (statements.length > 0) {
      PsiElement replacement = ReplacerUtil.copySpacesAndCommentsBefore(elementToReplace, statements, replacementToMake, elementParent);

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
        // preserve comments
        copyUnmatchedElements(elementToReplace, replacement);

        if (replacement instanceof PsiClass) {
          final PsiStatement[] searchStatements = getCodeBlock().getStatements();
          if (searchStatements.length > 0 &&
              searchStatements[0] instanceof PsiDeclarationStatement &&
              ((PsiDeclarationStatement)searchStatements[0]).getDeclaredElements()[0] instanceof PsiClass
            ) {
            final PsiClass replaceClazz = (PsiClass)replacement;
            final PsiClass queryClazz = (PsiClass)((PsiDeclarationStatement)searchStatements[0]).getDeclaredElements()[0];
            final PsiClass clazz = (PsiClass)elementToReplace;

            if (replaceClazz.getExtendsList().getTextLength() == 0 &&
                queryClazz.getExtendsList().getTextLength() == 0 &&
                clazz.getExtendsList().getTextLength() != 0
              ) {
              replaceClazz.addBefore(clazz.getExtendsList().getPrevSibling(), replaceClazz.getExtendsList()); // whitespace
              replaceClazz.getExtendsList().addRange(
                clazz.getExtendsList().getFirstChild(), clazz.getExtendsList().getLastChild()
              );
            }

            if (replaceClazz.getImplementsList().getTextLength() == 0 &&
                queryClazz.getImplementsList().getTextLength() == 0 &&
                clazz.getImplementsList().getTextLength() != 0
              ) {
              replaceClazz.addBefore(clazz.getImplementsList().getPrevSibling(), replaceClazz.getImplementsList()); // whitespace
              replaceClazz.getImplementsList().addRange(
                clazz.getImplementsList().getFirstChild(),
                clazz.getImplementsList().getLastChild()
              );
            }

            if (replaceClazz.getTypeParameterList().getTextLength() == 0 &&
                queryClazz.getTypeParameterList().getTextLength() == 0 &&
                clazz.getTypeParameterList().getTextLength() != 0
              ) {
              // skip < and >
              replaceClazz.getTypeParameterList().replace(
                clazz.getTypeParameterList()
              );
            }
          }
        }

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
            lastToDelete = PsiTreeUtil.skipSiblingsForward(nextSibling, PsiWhiteSpace.class);
            nextSibling = PsiTreeUtil.skipSiblingsForward(lastToDelete, PsiWhiteSpace.class);
          }
        }

        element.getParent().deleteChildRange(firstToDelete, lastToDelete);
      }
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
    final List<PsiJavaCodeReferenceElement> references = new ArrayList<>();
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
        if (reference.getTypeParameters().length != 0) {
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

  @Nullable
  private static PsiElement createSemicolon(final PsiElement space) throws IncorrectOperationException {
    final PsiStatement text = JavaPsiFacade.getInstance(space.getProject()).getElementFactory().createStatementFromText(";", null);
    return text.getFirstChild();
  }

  private static class Collector extends JavaRecursiveElementWalkingVisitor {
    private final HashMap<String, PsiNamedElement> namedElements = new HashMap<>(1);

    public static  Map<String, PsiNamedElement> collectNamedElements(PsiElement context) {
      final Collector collector = new Collector();
      context.accept(collector);
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

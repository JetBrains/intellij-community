package com.intellij.structuralsearch;

import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.xml.XmlText;
import com.intellij.structuralsearch.impl.matcher.JavaMatchingVisitor;
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
    boolean listContext = false;
    final PsiElement parent = el.getParent();

    if (parent instanceof PsiParameterList ||
        parent instanceof PsiExpressionList ||
        parent instanceof PsiCodeBlock ||
        parent instanceof PsiClass ||
        parent instanceof XmlText ||
        (parent instanceof PsiIfStatement &&
         (((PsiIfStatement)parent).getThenBranch() == el ||
          ((PsiIfStatement)parent).getElseBranch() == el
         )
        ) ||
        (parent instanceof PsiLoopStatement &&
         ((PsiLoopStatement)parent).getBody() == el
        )
      ) {
      listContext = true;
    }

    return listContext;
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
             ((PsiDeclarationStatement)replacement).getDeclaredElements().length == 1
      ) {
      return ((PsiDeclarationStatement)replacement).getDeclaredElements()[0];
    }
    else if (replacement instanceof PsiBlockStatement &&
             elementToReplace instanceof PsiCodeBlock
      ) {
      return ((PsiBlockStatement)replacement).getCodeBlock();
    }

    return replacement;
  }

  private boolean isSymbolReplacement(final PsiElement el) throws IncorrectOperationException {
    return getSymbolReplacementTarget(el) != null;
  }

  @SuppressWarnings({"unchecked", "ConstantConditions"})
  private void handleModifierList(final PsiElement el, final PsiElement replacement) throws IncorrectOperationException {
    // We want to copy all comments, including doc comments and modifier lists
    // that are present in matched nodes but not present in search/replace

    Map<String, String> newNameToSearchPatternNameMap = myContext.getNewName2PatternNameMap();

    ModifierListOwnerCollector collector = new ModifierListOwnerCollector();
    el.accept(collector);
    Map<String, PsiNamedElement> originalNamedElements = (Map<String, PsiNamedElement>)collector.namedElements.clone();
    collector.namedElements.clear();

    replacement.accept(collector);
    Map<String, PsiNamedElement> replacedNamedElements = (Map<String, PsiNamedElement>)collector.namedElements.clone();
    collector.namedElements.clear();

    if (originalNamedElements.size() == 0 && replacedNamedElements.size() == 0) {
      Replacer.handleComments(el, replacement, myContext);
      return;
    }

    final PsiStatement[] statements = getCodeBlock().getStatements();
    if (statements.length > 0) {
      statements[0].getParent().accept(collector);
    }

    Map<String, PsiNamedElement> searchedNamedElements = (Map<String, PsiNamedElement>)collector.namedElements.clone();
    collector.namedElements.clear();

    for (String name : originalNamedElements.keySet()) {
      PsiNamedElement originalNamedElement = originalNamedElements.get(name);
      PsiNamedElement replacementNamedElement = replacedNamedElements.get(name);
      String key = newNameToSearchPatternNameMap.get(name);
      if (key == null) key = name;
      PsiNamedElement searchNamedElement = searchedNamedElements.get(key);

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

      if (replacementNamedElement != null && searchNamedElement != null) {
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

        if (searchNamedElement instanceof PsiModifierListOwner) {
          PsiModifierList modifierListOfSearchedElement = ((PsiModifierListOwner)searchNamedElement).getModifierList();
          final PsiModifierListOwner modifierListOwner = ((PsiModifierListOwner)replacementNamedElement);
          PsiModifierList modifierListOfReplacement = modifierListOwner.getModifierList();

          if (modifierListOfSearchedElement.getTextLength() == 0 &&
              modifierListOfReplacement.getTextLength() == 0 &&
              modifierList.getTextLength() > 0
            ) {
            PsiElement space = modifierList.getNextSibling();
            if (!(space instanceof PsiWhiteSpace)) {
              space = createWhiteSpace(space);
            }

            modifierListOfReplacement.replace(modifierList);
            // copy space after modifier list
            if (space instanceof PsiWhiteSpace) {
              modifierListOwner.addRangeAfter(space, space, modifierListOwner.getModifierList());
            }
          } else if (modifierListOfSearchedElement.getTextLength() == 0 && modifierList.getTextLength() > 0) {
            modifierListOfReplacement.addRange(modifierList.getFirstChild(), modifierList.getLastChild());
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
    PsiElement elementParent = elementToReplace.getParent();
    String replacementToMake = info.getReplacement();
    PsiElement el = findRealSubstitutionElement(elementToReplace);
    boolean listContext = isListContext(el);

    if (el instanceof PsiAnnotation && !replacementToMake.isEmpty() && !StringUtil.startsWithChar(replacementToMake, '@')) {
      replacementToMake = "@" + replacementToMake;
    }

    PsiElement[] statements = ReplacerUtil
      .createTreeForReplacement(replacementToMake, el instanceof PsiMember && !isSymbolReplacement(el) ?
                                                   PatternTreeContext.Class :
                                                   PatternTreeContext.Block, myContext);

    if (el instanceof PsiAnnotation && statements.length == 1) {
      final PsiElement parent = el.getParent();
      final PsiElement statement = statements[0];
      if (statement instanceof PsiDeclarationStatement) {
        final PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement)statement;
        final PsiElement firstChild = declarationStatement.getFirstChild();
        if (firstChild instanceof PsiModifierList) {
          final PsiModifierList modifierList = (PsiModifierList)firstChild;
          for (PsiElement child : modifierList.getChildren()) {
            parent.add(child);
          }
        }
      }
      el.delete();
      return;
    }
    if (listContext) {
      if (statements.length > 1) {
        elementParent.addRangeBefore(statements[0], statements[statements.length - 1], elementToReplace);
      }
      else if (statements.length == 1) {
        PsiElement replacement = getMatchExpr(statements[0], elementToReplace);

        handleModifierList(el, replacement);
        replacement = handleSymbolReplacement(replacement, el);

        if (replacement instanceof PsiTryStatement) {
          final List<PsiCatchSection> unmatchedCatchSections = el.getUserData(JavaMatchingVisitor.UNMATCHED_CATCH_SECTION_CONTENT_VAR_KEY);
          if (unmatchedCatchSections != null) {
            final PsiTryStatement tryStatement = (PsiTryStatement)replacement;
            final PsiCatchSection[] catches = tryStatement.getCatchSections();
            final PsiElement anchor = catches.length == 0 ? tryStatement.getTryBlock() : catches[catches.length - 1];
            for (int i = unmatchedCatchSections.size() - 1; i >= 0; --i) {
              replacement.addAfter(unmatchedCatchSections.get(i), anchor);
              replacement.addAfter(createWhiteSpace(replacement), anchor);
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
          elementParent.addRangeBefore(replacement.getFirstChild(), prevLastChildInParent, el);
        }
        else {
          elementParent.addBefore(replacement.getFirstChild(), el);
        }

        el.getNode().getTreeParent().removeChild(el.getNode());
      }
      else {
        // preserve comments
        handleModifierList(el, replacement);

        if (replacement instanceof PsiClass) {
          // modifier list
          final PsiStatement[] searchStatements = getCodeBlock().getStatements();
          if (searchStatements.length > 0 &&
              searchStatements[0] instanceof PsiDeclarationStatement &&
              ((PsiDeclarationStatement)searchStatements[0]).getDeclaredElements()[0] instanceof PsiClass
            ) {
            final PsiClass replaceClazz = (PsiClass)replacement;
            final PsiClass queryClazz = (PsiClass)((PsiDeclarationStatement)searchStatements[0]).getDeclaredElements()[0];
            final PsiClass clazz = (PsiClass)el;

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

        replacement = handleSymbolReplacement(replacement, el);

        el.replace(replacement);
      }
    }
    else {
      final PsiElement nextSibling = el.getNextSibling();
      el.delete();
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
          prevSibling = prevSibling != null ? prevSibling.getPrevSibling() : null;
        }
        else if (prevSibling == null && nextSibling instanceof PsiWhiteSpace) {
          lastToDelete = nextSibling;
        }

        if (nextSibling instanceof XmlText && i + 1 < matchSize) {
          final PsiElement next = info.getMatch(i + 1);
          if (next != null && next == nextSibling.getNextSibling()) {
            lastToDelete = nextSibling;
          }
        }

        if (element instanceof PsiExpression) {
          final PsiElement parent = element.getParent().getParent();
          if ((parent instanceof PsiCall ||
               parent instanceof PsiAnonymousClass
          ) &&
              prevSibling instanceof PsiJavaToken &&
              ((PsiJavaToken)prevSibling).getTokenType() == JavaTokenType.COMMA
            ) {
            firstToDelete = prevSibling;
          }
        }
        else if (element instanceof PsiParameter &&
                 prevSibling instanceof PsiJavaToken &&
                 ((PsiJavaToken)prevSibling).getTokenType() == JavaTokenType.COMMA
          ) {
          firstToDelete = prevSibling;
        }

        element.getParent().deleteChildRange(firstToDelete, lastToDelete);
      }
    }
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
    final List<PsiJavaCodeReferenceElement> references = new ArrayList<PsiJavaCodeReferenceElement>();
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

  private static PsiElement createWhiteSpace(final PsiElement space) throws IncorrectOperationException {
    return PsiParserFacade.SERVICE.getInstance(space.getProject()).createWhiteSpaceFromText(" ");
  }

  private static class ModifierListOwnerCollector extends JavaRecursiveElementWalkingVisitor {
    HashMap<String, PsiNamedElement> namedElements = new HashMap<String, PsiNamedElement>(1);

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

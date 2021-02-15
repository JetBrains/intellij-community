// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.lang.regexp;

import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.TailTypeDecorator;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PlatformIcons;
import com.intellij.util.ProcessingContext;
import com.intellij.util.ui.EmptyIcon;
import org.intellij.lang.regexp.psi.RegExpProperty;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import static com.intellij.patterns.PlatformPatterns.psiElement;
import static com.intellij.patterns.StandardPatterns.or;

/**
 * @author vnikolaenko
 */
public final class RegExpCompletionContributor extends CompletionContributor {
  private static final Icon emptyIcon = EmptyIcon.create(PlatformIcons.PROPERTY_ICON);

  public RegExpCompletionContributor() {
    {
      final PsiElementPattern.Capture<PsiElement> namedCharacterPattern = psiElement().withText("\\N");
      extend(CompletionType.BASIC, psiElement().afterLeaf(namedCharacterPattern),
             new NamedCharacterCompletionProvider(true));
      extend(CompletionType.BASIC, psiElement().afterLeaf(psiElement(RegExpTT.LBRACE).afterLeaf(namedCharacterPattern)),
             new NamedCharacterCompletionProvider(false));

      extend(CompletionType.BASIC, psiElement().withText("\\I"), new CharacterClassesNameCompletionProvider());

      final ElementPattern<PsiElement> propertyPattern = psiElement().withText("\\p");
      extend(CompletionType.BASIC, psiElement().afterLeaf(propertyPattern), new PropertyCompletionProvider());

      final ElementPattern<PsiElement> bracketExpressionPattern = psiElement().afterLeaf(
        or(psiElement(RegExpTT.BRACKET_EXPRESSION_BEGIN),
           psiElement(RegExpTT.CARET).afterLeaf(psiElement(RegExpTT.BRACKET_EXPRESSION_BEGIN))));
      extend(CompletionType.BASIC, bracketExpressionPattern, new BracketExpressionCompletionProvider());
    }

    {
      // TODO: backSlashPattern is needed for reg exp in injected context, remove when unescaping will be performed by Injecting framework
      final PsiElementPattern.Capture<PsiElement> namedCharacterPattern = psiElement().withText("\\\\N");
      extend(CompletionType.BASIC, psiElement().afterLeaf(namedCharacterPattern),
             new NamedCharacterCompletionProvider(true));
      extend(CompletionType.BASIC, psiElement().afterLeaf(psiElement(RegExpTT.LBRACE).afterLeaf(namedCharacterPattern)),
             new NamedCharacterCompletionProvider(false));

      final ElementPattern<PsiElement> backSlashPattern = psiElement().withText("\\\\I");
      extend(CompletionType.BASIC, backSlashPattern, new CharacterClassesNameCompletionProvider());

      final ElementPattern<PsiElement> propertyPattern = psiElement().withText("\\\\p");
      extend(CompletionType.BASIC, psiElement().afterLeaf(propertyPattern), new PropertyCompletionProvider());
      
      extend(CompletionType.BASIC, psiElement().inside(RegExpProperty.class).afterLeaf(psiElement(RegExpTT.EQ)),
             new PropertyValueCompletionProvider());
    }

    {
      // TODO: this seems to be needed only for tests!
      final ElementPattern<PsiElement> backSlashPattern = psiElement().withText("\\\\");
      extend(CompletionType.BASIC, psiElement().afterLeaf(backSlashPattern), new CharacterClassesNameCompletionProvider());

      final ElementPattern<PsiElement> propertyPattern
              = psiElement().withText("p").afterLeaf(backSlashPattern);
      extend(CompletionType.BASIC, psiElement().afterLeaf(propertyPattern), new PropertyCompletionProvider());

      final PsiElementPattern.Capture<PsiElement> namedCharacterPattern = psiElement().withText("N");
      extend(CompletionType.BASIC, psiElement().afterLeaf(namedCharacterPattern),
             new NamedCharacterCompletionProvider(true));
      extend(CompletionType.BASIC, psiElement().afterLeaf(psiElement(RegExpTT.LBRACE).afterLeaf(namedCharacterPattern)),
             new NamedCharacterCompletionProvider(false));
    }
  }

  private static void addLookupElement(final CompletionResultSet result, @NonNls final String name, String type, Icon icon) {
    result.addElement(createLookupElement(name, type, icon));
  }

  private static LookupElement createLookupElement(String name, String type, Icon icon) {
    return LookupElementBuilder.create(name).withTypeText(type).withIcon(icon);
  }

  private static class BracketExpressionCompletionProvider extends CompletionProvider<CompletionParameters> {

    @Override
    protected void addCompletions(@NotNull CompletionParameters parameters,
                                  @NotNull ProcessingContext context,
                                  @NotNull CompletionResultSet result) {

      for (String[] completion : RegExpLanguageHosts.getInstance().getPosixCharacterClasses(parameters.getPosition())) {
        result.addElement(
          LookupElementBuilder.create(completion[0]).withTypeText((completion.length > 1) ? completion[1] : null).withIcon(emptyIcon)
            .withInsertHandler(new InsertHandler<>() {
              @Override
              public void handleInsert(@NotNull InsertionContext context, @NotNull LookupElement item) {
                context.setAddCompletionChar(false);
                final Editor editor = context.getEditor();
                final Document document = editor.getDocument();
                final int tailOffset = context.getTailOffset();
                if (document.getTextLength() < tailOffset + 2 ||
                    !document.getText(new TextRange(tailOffset, tailOffset + 2)).equals(":]")) {
                  document.insertString(tailOffset, ":]");
                }
                editor.getCaretModel().moveCaretRelatively(2, 0, false, false, true);
              }
            }));
      }
    }
  }

  private static class PropertyCompletionProvider extends CompletionProvider<CompletionParameters> {

    @Override
    public void addCompletions(@NotNull final CompletionParameters parameters,
                               @NotNull final ProcessingContext context,
                               @NotNull final CompletionResultSet result) {
      for (String[] stringArray : RegExpLanguageHosts.getInstance().getAllKnownProperties(parameters.getPosition())) {
        addLookupElement(result, "{" + stringArray[0] + "}", stringArray.length > 1 ? stringArray[1]:null, PlatformIcons.PROPERTY_ICON);
      }
    }
  }

  private static class CharacterClassesNameCompletionProvider extends CompletionProvider<CompletionParameters> {

    @Override
    public void addCompletions(@NotNull final CompletionParameters parameters,
                               @NotNull final ProcessingContext context,
                               @NotNull final CompletionResultSet result)
    {
      for (final String[] completion : RegExpLanguageHosts.getInstance().getKnownCharacterClasses(parameters.getPosition())) {
        addLookupElement(result, completion[0], completion[1], emptyIcon);
      }

      for (String[] stringArray : RegExpLanguageHosts.getInstance().getAllKnownProperties(parameters.getPosition())) {
        addLookupElement(result, "p{" + stringArray[0] + "}", stringArray.length > 1? stringArray[1]:null, PlatformIcons.PROPERTY_ICON);
      }
    }
  }

  private static class NamedCharacterCompletionProvider extends CompletionProvider<CompletionParameters> {

    private final boolean myEmbrace;

    NamedCharacterCompletionProvider(boolean embrace) {
      myEmbrace = embrace;
    }

    @Override
    protected void addCompletions(@NotNull CompletionParameters parameters,
                                  @NotNull ProcessingContext context,
                                  @NotNull CompletionResultSet result) {
      UnicodeCharacterNames.iterate(name -> {
        if (result.getPrefixMatcher().prefixMatches(name)) {
          final String type = new String(new int[] {UnicodeCharacterNames.getCodePoint(name)}, 0, 1);
          if (myEmbrace) {
            result.addElement(createLookupElement("{" + name + "}", type, emptyIcon));
          }
          else {
            result.addElement(TailTypeDecorator.withTail(createLookupElement(name, type, emptyIcon), TailType.createSimpleTailType('}')));
          }
        }
        ProgressManager.checkCanceled();
      });
    }
  }

  private static class PropertyValueCompletionProvider extends CompletionProvider<CompletionParameters> {
    @Override
    protected void addCompletions(@NotNull CompletionParameters parameters,
                                  @NotNull ProcessingContext context,
                                  @NotNull CompletionResultSet result) {
      RegExpProperty property = ObjectUtils.tryCast(parameters.getPosition().getParent(), RegExpProperty.class);
      ASTNode propertyNameNode = property != null ? property.getCategoryNode() : null;
      if (propertyNameNode == null) {
        return;
      }
      for (String[] value : RegExpLanguageHosts.getInstance().getAllPropertyValues(property, propertyNameNode.getText())) {
        addLookupElement(result, value[0], value.length > 1 ? value[1] : null, null);
      }
    }
  }
}

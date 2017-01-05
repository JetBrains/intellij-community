/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.lang.regexp;

import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.TailTypeDecorator;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.util.PlatformIcons;
import com.intellij.util.ProcessingContext;
import com.intellij.util.ui.EmptyIcon;
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

      final ElementPattern<PsiElement> propertyNamePattern = psiElement().afterLeaf(psiElement().withText("{").afterLeaf(propertyPattern));
      extend(CompletionType.BASIC, propertyNamePattern, new PropertyNameCompletionProvider());

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

      final ElementPattern<PsiElement> propertyNamePattern
              = psiElement().afterLeaf(psiElement().withText("{").afterLeaf(propertyPattern));
      extend(CompletionType.BASIC, propertyNamePattern, new PropertyNameCompletionProvider());
    }

    {
      // TODO: this seems to be needed only for tests!
      final ElementPattern<PsiElement> backSlashPattern = psiElement().withText("\\\\");
      extend(CompletionType.BASIC, psiElement().afterLeaf(backSlashPattern), new CharacterClassesNameCompletionProvider());

      final ElementPattern<PsiElement> propertyPattern
              = psiElement().withText("p").afterLeaf(backSlashPattern);
      extend(CompletionType.BASIC, psiElement().afterLeaf(propertyPattern), new PropertyCompletionProvider());

      final ElementPattern<PsiElement> propertyNamePattern
              = psiElement().afterLeaf(psiElement().withText("{").afterLeaf(propertyPattern));
      extend(CompletionType.BASIC, propertyNamePattern, new PropertyNameCompletionProvider());

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
                                  ProcessingContext context,
                                  @NotNull CompletionResultSet result) {

      for (String[] completion : RegExpLanguageHosts.getInstance().getPosixCharacterClasses(parameters.getPosition())) {
        result.addElement(
          LookupElementBuilder.create(completion[0]).withTypeText((completion.length > 1) ? completion[1] : null).withIcon(emptyIcon)
            .withInsertHandler(new InsertHandler<LookupElement>() {
              @Override
              public void handleInsert(InsertionContext context, LookupElement item) {
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

  private static class PropertyNameCompletionProvider extends CompletionProvider<CompletionParameters> {

    @Override
    public void addCompletions(@NotNull final CompletionParameters parameters,
                               final ProcessingContext context,
                               @NotNull final CompletionResultSet result) {
      for (String[] stringArray : RegExpLanguageHosts.getInstance().getAllKnownProperties(parameters.getPosition())) {
        result.addElement(
          TailTypeDecorator.withTail(createLookupElement(stringArray[0], null, emptyIcon), TailType.createSimpleTailType('}')));
      }
    }
  }

  private static class PropertyCompletionProvider extends CompletionProvider<CompletionParameters> {

    @Override
    public void addCompletions(@NotNull final CompletionParameters parameters,
                               final ProcessingContext context,
                               @NotNull final CompletionResultSet result) {
      for (String[] stringArray : RegExpLanguageHosts.getInstance().getAllKnownProperties(parameters.getPosition())) {
        addLookupElement(result, "{" + stringArray[0] + "}", stringArray.length > 1 ? stringArray[1]:null, PlatformIcons.PROPERTY_ICON);
      }
    }
  }

  private static class CharacterClassesNameCompletionProvider extends CompletionProvider<CompletionParameters> {

    @Override
    public void addCompletions(@NotNull final CompletionParameters parameters,
                               final ProcessingContext context,
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

    public NamedCharacterCompletionProvider(boolean embrace) {
      myEmbrace = embrace;
    }

    @Override
    protected void addCompletions(@NotNull CompletionParameters parameters,
                                  ProcessingContext context,
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
}

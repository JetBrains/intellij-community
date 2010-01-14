/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.util.Icons;
import com.intellij.util.ProcessingContext;
import com.intellij.util.ui.EmptyIcon;
import org.intellij.lang.regexp.psi.impl.RegExpPropertyImpl;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import static com.intellij.patterns.PlatformPatterns.psiElement;

/**
 * @author vnikolaenko
 */
public class RegExpCompletionContributor extends CompletionContributor {
  private static final Icon emptyIcon = new EmptyIcon(Icons.PROPERTY_ICON.getIconWidth(), Icons.PROPERTY_ICON.getIconHeight());

  public RegExpCompletionContributor() {
    {
      extend(CompletionType.BASIC, psiElement().withText("\\I"), new CharacterClassesNameCompletionProvider());

      final ElementPattern<PsiElement> propertyPattern = psiElement().withText("\\p");
      extend(CompletionType.BASIC, psiElement().afterLeaf(propertyPattern), new PropertyCompletionProvider());

      final ElementPattern<PsiElement> propertyNamePattern = psiElement().afterLeaf(psiElement().withText("{").afterLeaf(propertyPattern));
      extend(CompletionType.BASIC, propertyNamePattern, new PropertyNameCompletionProvider());
    }

    {
      // TODO: backSlashPattern is needed for reg exp in injected context, remove when unescaping will be performed by Injecting framework
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
    }
  }

  private static void addLookupElement(final CompletionResultSet result, @NonNls final String name, String type, Icon icon) {
    result.addElement(createLookupElement(name, type, icon));
  }

  private static LookupElement createLookupElement(String name, String type, Icon icon) {
    return LookupElementBuilder.create(name).setTypeText(type).setIcon(icon);
  }

  private static class PropertyNameCompletionProvider extends CompletionProvider<CompletionParameters> {

    public void addCompletions(@NotNull final CompletionParameters parameters,
                               final ProcessingContext context,
                               @NotNull final CompletionResultSet result) {
      for (String[] stringArray : RegExpPropertyImpl.PROPERTY_NAMES) {
        result.addElement(
          TailTypeDecorator.withTail(createLookupElement(stringArray[0], null, emptyIcon), TailType.createSimpleTailType('}')));
      }
    }
  }

  private static class PropertyCompletionProvider extends CompletionProvider<CompletionParameters> {

    public void addCompletions(@NotNull final CompletionParameters parameters,
                               final ProcessingContext context,
                               @NotNull final CompletionResultSet result) {
      for (String[] stringArray : RegExpPropertyImpl.PROPERTY_NAMES) {
        addLookupElement(result, "{" + stringArray[0] + "}", stringArray.length > 1 ? stringArray[1]:null, Icons.PROPERTY_ICON);
      }
    }
  }

  private static class CharacterClassesNameCompletionProvider extends CompletionProvider<CompletionParameters> {

    public void addCompletions(@NotNull final CompletionParameters parameters,
                               final ProcessingContext context,
                               @NotNull final CompletionResultSet result) {
      @NonNls String[] completions = {"d", "D", "s", "S", "w", "W", "b", "B", "A", "G", "Z", "z", "Q", "E", "t", "n", "r", "f", "a", "e"};
      @NonNls String[] completionsTypes = {"digit: [0-9]", "nondigit: [^0-9]", "whitespace [ \\t\\n\\x0B\\f\\r]", "non-whitespace [^\\s]",
        "word character [a-zA-Z_0-9]", "nonword character [^\\w]", "word boundary", "non-word boundary", "beginning of the input",
        "end of the previous match", "end of the input but for the final terminator, if any", "end of input",
        "Nothing, but quotes all characters until \\E", " \tNothing, but ends quoting started by \\Q", "tab character ('\\u0009')",
        "newline (line feed) character ('\\u000A')", "carriage-return character ('\\u000D')", "form-feed character ('\\u000C')",
        "alert (bell) character ('\\u0007')", "escape character ('\\u001B')"};
      
      for (int i = 0; i < completions.length; ++i) {
        addLookupElement(result, completions[i], completionsTypes[i], emptyIcon);
      }

      for (String[] stringArray : RegExpPropertyImpl.PROPERTY_NAMES) {
        addLookupElement(result, "p{" + stringArray[0] + "}", stringArray.length > 1? stringArray[1]:null, Icons.PROPERTY_ICON);
      }
    }
  }
}

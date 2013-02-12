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
import com.intellij.util.PlatformIcons;
import com.intellij.util.ProcessingContext;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import static com.intellij.patterns.PlatformPatterns.psiElement;

/**
 * @author vnikolaenko
 */
public final class RegExpCompletionContributor extends CompletionContributor {
  private static final Icon emptyIcon = new EmptyIcon(PlatformIcons.PROPERTY_ICON.getIconWidth(), PlatformIcons.PROPERTY_ICON.getIconHeight());

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
    return LookupElementBuilder.create(name).withTypeText(type).withIcon(icon);
  }

  private static class PropertyNameCompletionProvider extends CompletionProvider<CompletionParameters> {

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

    public void addCompletions(@NotNull final CompletionParameters parameters,
                               final ProcessingContext context,
                               @NotNull final CompletionResultSet result) {
      for (String[] stringArray : RegExpLanguageHosts.getInstance().getAllKnownProperties(parameters.getPosition())) {
        addLookupElement(result, "{" + stringArray[0] + "}", stringArray.length > 1 ? stringArray[1]:null, PlatformIcons.PROPERTY_ICON);
      }
    }
  }

  private static class CharacterClassesNameCompletionProvider extends CompletionProvider<CompletionParameters> {

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
}

package org.intellij.lang.regexp;

import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementFactory;
import com.intellij.codeInsight.lookup.MutableLookupElement;
import com.intellij.patterns.ElementPattern;
import static com.intellij.patterns.PlatformPatterns.psiElement;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;
import org.intellij.lang.regexp.psi.impl.RegExpPropertyImpl;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * User: vnikolaenko
 * Date: 22.09.2008
 * Time: 12:14:14
 * To change this template use File | Settings | File Templates.
 */
public class RegExpCompletionContributor extends CompletionContributor {
    public RegExpCompletionContributor() {
        final ElementPattern<PsiElement> backSlashPattern = psiElement().withText("\\\\");
        extend(CompletionType.BASIC, psiElement().afterLeaf(backSlashPattern), new CharacterClassesNameCompletionProvider());

        final ElementPattern<PsiElement> propertyPattern
                = psiElement().withText("p").afterLeaf(backSlashPattern);
        extend(CompletionType.BASIC, psiElement().afterLeaf(propertyPattern), new PropertyCompletionProvider());

        final ElementPattern<PsiElement> propertyNamePattern
                = psiElement().afterLeaf(psiElement().withText("{").afterLeaf(propertyPattern));
        extend(CompletionType.BASIC, propertyNamePattern, new PropertyNameCompletionProvider());
    }

    private static MutableLookupElement<String> addLookupElement(final CompletionResultSet result,
                                                                 final String name) {
        MutableLookupElement<String> element = LookupElementFactory.getInstance().createLookupElement(name);
        result.addElement(element);
        return element;
    }

    private static class PropertyNameCompletionProvider extends CompletionProvider<CompletionParameters> {

        public void addCompletions(@NotNull final CompletionParameters parameters,
                                   final ProcessingContext context,
                                   @NotNull final CompletionResultSet result) {
              for (String[] stringArray : RegExpPropertyImpl.PROPERTY_NAMES) {
                  addLookupElement(result, stringArray[0]).setTailType(TailType.createSimpleTailType('}'));
              }
        }
    }

    private static class PropertyCompletionProvider extends CompletionProvider<CompletionParameters> {

        public void addCompletions(@NotNull final CompletionParameters parameters,
                                   final ProcessingContext context,
                                   @NotNull final CompletionResultSet result) {
            for (String[] stringArray : RegExpPropertyImpl.PROPERTY_NAMES) {
                addLookupElement(result, "{" + stringArray[0] + "}");
            }
        }
    }
    private static class CharacterClassesNameCompletionProvider extends CompletionProvider<CompletionParameters> {

        public void addCompletions(@NotNull final CompletionParameters parameters,
                                   final ProcessingContext context,
                                   @NotNull final CompletionResultSet result) {
          String[] completions = {
                "d", "D", "s", "S", "w", "W", "b", "B", "A", "G", "Z", "z", "Q", "E",
                "t", "n", "r", "f", "a", "e"
            };
            for (String s : completions) {
                addLookupElement(result, s);
            }
            for (String[] stringArray : RegExpPropertyImpl.PROPERTY_NAMES) {
                addLookupElement(result, "p{" + stringArray[0] + "}");
            }
        }
    }
}

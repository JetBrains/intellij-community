package org.intellij.lang.regexp;

import com.intellij.lang.documentation.QuickDocumentationProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import org.intellij.lang.regexp.psi.impl.RegExpPropertyImpl;
import org.intellij.lang.regexp.psi.impl.RegExpElementImpl;
import org.intellij.lang.regexp.psi.RegExpGroup;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: vnikolaenko
 * Date: 17.09.2008
 * Time: 19:24:29
 * To change this template use File | Settings | File Templates.
 */
public class RegExpDocumentionProvider extends QuickDocumentationProvider {
    @Nullable
    public String getUrlFor(PsiElement element, PsiElement originalElement) {
        return null;
    }

    @Nullable
    public String generateDoc(PsiElement element, PsiElement originalElement) {
        if (element instanceof RegExpPropertyImpl) {
            String elementName = ((RegExpPropertyImpl)element).getCategoryNode().getText();
            for (String[] stringArray : RegExpPropertyImpl.PROPERTY_NAMES) {
                if (stringArray[0].equals(elementName)) {
                    return "Property block stands for " + stringArray[1];
                }
            }
        }
        return null;
    }

    @Nullable
    public PsiElement getDocumentationElementForLookupItem(PsiManager psiManager, Object object, PsiElement element){
        return null;
    }

    @Nullable
    public PsiElement getDocumentationElementForLink(PsiManager psiManager, String link, PsiElement context) {
        return null;
    }

    @Nullable
    public String getQuickNavigateInfo(PsiElement element) {
        if (element instanceof RegExpGroup) {
            return "Capturing Group: " + ((RegExpElementImpl)element).getUnescapedText();
        } else {
            return null;
        }
    }
}

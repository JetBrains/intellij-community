/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Jul 23, 2002
 * Time: 3:01:22 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.CharFilter;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.lang.Language;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class DefaultCharFilter implements CharFilter {
  private final PsiFile myFile;
  private boolean myWithinLiteral;
  private CharFilter myDelegate = null;

  public DefaultCharFilter(Editor editor,PsiFile file, int offset) {
    myFile = file;

    PsiElement psiElement;
    if (offset > 0) psiElement = file.findElementAt(offset - 1);
    else {
      psiElement = file.findElementAt(offset + 1);
      if (psiElement == null && offset > 0) psiElement = file.findElementAt(offset - 1);
    }
    
    if (psiElement != null) myDelegate = ourCharFilterRegistry.get(psiElement.getLanguage());

    if (myFile instanceof XmlFile && myDelegate == null) {
      boolean inJavaContext = false;

      if (psiElement != null) {
        PsiElement elementToTest = psiElement;
        if (elementToTest instanceof PsiWhiteSpace) {
          elementToTest = elementToTest.getParent(); // JSPX has whitespace with language Java
        }

        final Language language = elementToTest.getLanguage();
        if (StdLanguages.JAVA.equals(language) || language.getID().equals("JavaScript")) {
          inJavaContext = true;
        }
      }
      
      if (!inJavaContext) {
        final PsiElement parentElement = psiElement.getParent() != null ? psiElement.getParent():null;
        String s;
        final boolean withinTag = parentElement != null &&
                                  ( parentElement instanceof XmlTag ||
                                    ( parentElement instanceof PsiErrorElement &&
                                      parentElement.getParent() instanceof XmlDocument
                                    ) ||
        ((parentElement instanceof XmlDocument || parentElement instanceof XmlText) &&
         ((s = psiElement.getText()).equals("<") || s.equals("\""))));
        myDelegate = PsiUtil.isInJspFile(myFile) ? new JspCharFilter(withinTag, editor) : new XmlCharFilter(withinTag, editor);
      }
    } else {

      if (psiElement != null && psiElement.getParent() instanceof PsiLiteralExpression) {
        myWithinLiteral = true;
      }
    }
  }

  public int accept(char c, final String prefix, final LookupElement element) {
    if (element != null) {
      for (final String string : ((LookupItem<?>)element).getAllLookupStrings()) {
        if (string.startsWith(prefix + c)) return ADD_TO_PREFIX;
      }
    }

    if (myDelegate != null) return myDelegate.accept(c, prefix, element);

    if (Character.isJavaIdentifierPart(c)) return CharFilter.ADD_TO_PREFIX;
    switch(c){
      case '.': if (myWithinLiteral) return CharFilter.ADD_TO_PREFIX;
      case ',':
      case ';':
      case '=':
      case ' ':
      case ':':
      case '(':
        return CharFilter.SELECT_ITEM_AND_FINISH_LOOKUP;

      default:
        return CharFilter.HIDE_LOOKUP;
    }
  }

  private static Map<Language,CharFilter> ourCharFilterRegistry = new HashMap<Language, CharFilter>();

  public static void registerFilter(@NotNull Language language,@NotNull  CharFilter filter) {
    ourCharFilterRegistry.put(language, filter);
  }
}

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
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.lang.StdLanguages;
import com.intellij.lang.Language;

public class DefaultCharFilter implements CharFilter {
  private final PsiFile myFile;
  private boolean myWithinLiteral;
  private CharFilter myDelegate = null;

  public DefaultCharFilter(PsiFile file, int offset) {
    myFile = file;

    final PsiElement psiElement = file.findElementAt(offset);

    if (myFile instanceof XmlFile) {
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
        myDelegate = PsiUtil.isInJspFile(myFile) ? new JspCharFilter() : new XmlCharFilter();
      }
    } else {

      if (psiElement != null && psiElement.getParent() instanceof PsiLiteralExpression) {
        myWithinLiteral = true;
      }
    }
  }

  public int accept(char c) {
    if (myDelegate != null) return myDelegate.accept(c);

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
}

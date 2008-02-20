/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Jul 23, 2002
 * Time: 3:15:07 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.editorActions.XmlAutoPopupHandler;
import com.intellij.codeInsight.lookup.CharFilter;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.lang.Language;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import org.jetbrains.annotations.NotNull;

public class XmlCharFilter extends CharFilter {

  public static boolean isInXmlContext(Lookup lookup) {
    PsiElement psiElement = lookup.getPsiElement();
    final PsiFile file = lookup.getPsiFile();

    if (file instanceof XmlFile) {
      if (psiElement != null) {
        PsiElement elementToTest = psiElement;
        if (elementToTest instanceof PsiWhiteSpace) {
          elementToTest = elementToTest.getParent(); // JSPX has whitespace with language Java
        }

        final Language language = elementToTest.getLanguage();
        if (StdLanguages.JAVA.equals(language) || language.getID().equals("JavaScript")) {
          return false;
        }
        return true;
      }
    }
    return false;
  }

  public static boolean isWithinTag(Lookup lookup) {
    if (isInXmlContext(lookup)) {
      PsiElement psiElement = lookup.getPsiElement();
      final PsiElement parentElement = psiElement.getParent() != null ? psiElement.getParent():null;
      String s;
      return parentElement != null &&
             ( parentElement instanceof XmlTag ||
               ( parentElement instanceof PsiErrorElement &&
                 parentElement.getParent() instanceof XmlDocument
               ) ||
                 ((parentElement instanceof XmlDocument || parentElement instanceof XmlText) &&
                  ((s = psiElement.getText()).equals("<") || s.equals("\""))));
    }
    return false;
  }

  public Result acceptChar(char c, @NotNull final String prefix, final Lookup lookup) {
    if (!isInXmlContext(lookup)) return null;

    if (Character.isJavaIdentifierPart(c)) return Result.ADD_TO_PREFIX;
    switch(c){
      case ':':
      case '.':
      case '-':
        return Result.ADD_TO_PREFIX;
      case ',':
      case ';':
      case '=':
      case '(':

      case '/':
        if (isWithinTag(lookup)) {
          if (StringUtil.isNotEmpty(prefix)) {
            return Result.SELECT_ITEM_AND_FINISH_LOOKUP;
          }
          XmlAutoPopupHandler.autoPopupXmlLookup(lookup.getEditor().getProject(), lookup.getEditor());
          return Result.HIDE_LOOKUP;
        }
        return Result.ADD_TO_PREFIX;
        
      case '>': if (StringUtil.isNotEmpty(prefix)) {
        return Result.SELECT_ITEM_AND_FINISH_LOOKUP;
      }

      default:
        return Result.HIDE_LOOKUP;
      case ' ':
        return Result.SELECT_ITEM_AND_FINISH_LOOKUP;
    }
  }
}

package com.intellij.codeInsight.hint;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;

import java.util.Map;
import java.util.HashMap;

public class DeclarationRangeUtil {
  public static Map<Class,DeclarationRangeHandler> ourDeclarationRangeRegistry = new HashMap<Class, DeclarationRangeHandler>();

  private DeclarationRangeUtil() {
  }

  public static void setDeclarationHandler(@NotNull Class clazz, DeclarationRangeHandler handler) {
    ourDeclarationRangeRegistry.put(clazz, handler);
  }// Q: not a good place?
  public static @NotNull TextRange getDeclarationRange(PsiElement container) {
    final TextRange textRange = getPossibleDeclarationAtRange(container);
    assert textRange != null :"Declaration range is invalid for "+container.getClass();
    return textRange;
  }

  public static @Nullable
  TextRange getPossibleDeclarationAtRange(final PsiElement container) {
    if (container instanceof PsiMethod){
      PsiMethod method = (PsiMethod)container;
      int startOffset = method.getModifierList().getTextRange().getStartOffset();
      int endOffset = method.getThrowsList().getTextRange().getEndOffset();
      return new TextRange(startOffset, endOffset);
    }
    else if (container instanceof PsiClass){
      PsiClass aClass = (PsiClass)container;
      if (aClass instanceof PsiAnonymousClass){
        PsiConstructorCall call = (PsiConstructorCall)aClass.getParent();
        int startOffset = call.getTextRange().getStartOffset();
        int endOffset = call.getArgumentList().getTextRange().getEndOffset();
        return new TextRange(startOffset, endOffset);
      }
      else{
        int startOffset = aClass.getModifierList().getTextRange().getStartOffset();
        int endOffset = aClass.getImplementsList().getTextRange().getEndOffset();
        return new TextRange(startOffset, endOffset);
      }
    }
    else if (container instanceof PsiClassInitializer){
      PsiClassInitializer initializer = (PsiClassInitializer)container;
      int startOffset = initializer.getModifierList().getTextRange().getStartOffset();
      int endOffset = initializer.getBody().getTextRange().getStartOffset();
      return new TextRange(startOffset, endOffset);
    }
    else if (container instanceof XmlTag){
      XmlTag xmlTag = (XmlTag)container;
      int endOffset = xmlTag.getTextRange().getStartOffset();

      for (PsiElement child = xmlTag.getFirstChild(); child != null; child = child.getNextSibling()) {
        endOffset = child.getTextRange().getEndOffset();
        if (child instanceof XmlToken) {
          XmlToken token = (XmlToken)child;
          IElementType tokenType = token.getTokenType();
          if (tokenType == XmlTokenType.XML_EMPTY_ELEMENT_END || tokenType == XmlTokenType.XML_TAG_END) break;
        }
      }

      return new TextRange(xmlTag.getTextRange().getStartOffset(), endOffset);
    }
    else {
      for(Class clazz:ourDeclarationRangeRegistry.keySet()) {
        if (clazz.isInstance(container)) {
          final DeclarationRangeHandler handler = ourDeclarationRangeRegistry.get(clazz);
          if (handler != null) return handler.getDeclarationRange(container);
        }
      }

      return null;
    }
  }

  public interface DeclarationRangeHandler {
    @NotNull
    TextRange getDeclarationRange(@NotNull PsiElement container);
  }
}
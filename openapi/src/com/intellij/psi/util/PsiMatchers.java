/*
 * @author max
 */
package com.intellij.psi.util;

import com.intellij.psi.*;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

public class PsiMatchers {

  private PsiMatchers() {
  }

  public static PsiMatcherExpression hasModifier(@Modifier final String modifier, final boolean shouldHave) {
    return new PsiMatcherExpression() {
      public Boolean match(PsiElement element) {
        PsiModifierListOwner owner = element instanceof PsiModifierListOwner ? (PsiModifierListOwner) element : null;

        if (owner != null && owner.hasModifierProperty(modifier) == shouldHave) return Boolean.TRUE;
        return Boolean.FALSE;
      }
    };
  }

  public static PsiMatcherExpression hasText(final String text) {
    return new PsiMatcherExpression() {
      public Boolean match(PsiElement element) {
        if (element.getTextLength() != text.length()) return Boolean.FALSE;
        return text.equals(element.getText());
      }
    };
  }

  public static PsiMatcherExpression hasText(@NotNull final String... texts) {
    return new PsiMatcherExpression() {
      public Boolean match(PsiElement element) {
        String text = element.getText();
        return ArrayUtil.find(texts, text) != -1;
      }
    };
  }

  public static PsiMatcherExpression hasClass(final Class aClass) {
    return new PsiMatcherExpression() {
      public Boolean match(PsiElement element) {
        if (aClass.isAssignableFrom(element.getClass())) return Boolean.TRUE;
        return Boolean.FALSE;
      }
    };
  }

  public static PsiMatcherExpression hasClass(final Class[] classes) {
    return new PsiMatcherExpression() {
      public Boolean match(PsiElement element) {
        for (Class aClass : classes) {
          if (aClass.isAssignableFrom(element.getClass())) return Boolean.TRUE;
        }
        return Boolean.FALSE;
      }
    };
  }

  public static PsiMatcherExpression hasName(final String name) {
    return new PsiMatcherExpression() {
      public Boolean match(PsiElement element) {
        if (element instanceof PsiNamedElement && name.equals(((PsiNamedElement) element).getName())) return Boolean.TRUE;
        if (element instanceof XmlTag && name.equals(((XmlTag) element).getName())) return Boolean.TRUE;
        return Boolean.FALSE;
      }
    };
  }

  public static PsiMatcherExpression hasTagValue(final String value) {
    return new PsiMatcherExpression() {
      public Boolean match(PsiElement element) {
        if (element instanceof XmlTag && value.equals(((XmlTag) element).getValue().getTrimmedText())) return Boolean.TRUE;
        return Boolean.FALSE;
      }
    };
  }

  public static PsiMatcherExpression isConstructor(final boolean shouldBe) {
    return new PsiMatcherExpression() {
      public Boolean match(PsiElement element) {
        return element instanceof PsiMethod && ((PsiMethod)element).isConstructor() == shouldBe;
      }
    };
  }
}
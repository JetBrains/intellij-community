/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.psi.util;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.xml.XmlTag;

public class PsiMatcherImpl implements PsiMatcher {
  PsiElement myElement;

  public PsiMatcherImpl(PsiElement element) {
    myElement = element;
  }

  public PsiMatcher parent(PsiMatcherExpression e) {
    myElement = myElement.getParent();
    if (myElement == null || (e != null && e.match(myElement) != Boolean.TRUE)) return NullPsiMatcherImpl.INSTANCE;
    return this;
  }

  public PsiMatcher firstChild(PsiMatcherExpression e) {
    final PsiElement[] children = myElement.getChildren();
    for (PsiElement child : children) {
      myElement = child;
      if (e == null || e.match(myElement) == Boolean.TRUE) {
        return this;
      }
    }
    return NullPsiMatcherImpl.INSTANCE;
  }

  public PsiMatcher ancestor(PsiMatcherExpression e) {
    Boolean res;
    while (myElement != null) {
      res = e == null ? Boolean.TRUE : e.match(myElement);
      if (res == Boolean.TRUE) break;
      if (res == null) return NullPsiMatcherImpl.INSTANCE;
      myElement = myElement.getParent();
    }
    if (myElement == null) return NullPsiMatcherImpl.INSTANCE;
    return this;
  }

  public PsiMatcher descendant(PsiMatcherExpression e) {
    final PsiElement[] children = myElement.getChildren();
    for (PsiElement child : children) {
      myElement = child;
      final Boolean res = e == null ? Boolean.TRUE : e.match(myElement);
      if (res == Boolean.TRUE) {
        return this;
      }
      else if (res == Boolean.FALSE) {
        final PsiMatcher grandChild = descendant(e);
        if (grandChild != NullPsiMatcherImpl.INSTANCE) return grandChild;
      }
    }
    return NullPsiMatcherImpl.INSTANCE;
  }

  public PsiMatcher dot(PsiMatcherExpression e) {
    return e == null || e.match(myElement) == Boolean.TRUE ? this : NullPsiMatcherImpl.INSTANCE;
  }


  public PsiElement getElement() {
    return myElement;
  }

  public static PsiMatcherExpression hasModifier(final String modifier, final boolean shouldHave) {
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
        if (text.equals(element.getText())) return Boolean.TRUE;
        return Boolean.FALSE;
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

  private static class NullPsiMatcherImpl implements PsiMatcher {
    public PsiMatcher parent(PsiMatcherExpression e) {
      return this;
    }

    public PsiMatcher firstChild(PsiMatcherExpression e) {
      return this;
    }

    public PsiMatcher ancestor(PsiMatcherExpression e) {
      return this;
    }

    public PsiMatcher descendant(PsiMatcherExpression e) {
      return this;
    }

    public PsiMatcher dot(PsiMatcherExpression e) {
      return this;
    }

    public PsiElement getElement() {
      return null;
    }

    private static final NullPsiMatcherImpl INSTANCE = new NullPsiMatcherImpl();
  }
}

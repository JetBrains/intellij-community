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

import com.intellij.lang.ASTNode;
import com.intellij.lang.documentation.QuickDocumentationProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import org.intellij.lang.regexp.psi.RegExpElement;
import org.intellij.lang.regexp.psi.RegExpGroup;
import org.intellij.lang.regexp.psi.RegExpProperty;
import org.intellij.lang.regexp.psi.impl.RegExpPropertyImpl;
import org.jetbrains.annotations.Nullable;

/**
 * @author vnikolaenko
 */
public class RegExpDocumentationProvider extends QuickDocumentationProvider {
  @Override
  @Nullable
  public String generateDoc(PsiElement element, PsiElement originalElement) {
    if (element instanceof RegExpProperty) {
      final RegExpProperty prop = (RegExpProperty)element;
      final ASTNode node = prop.getCategoryNode();
      if (node != null) {
        final String elementName = node.getText();
        for (String[] stringArray : RegExpPropertyImpl.PROPERTY_NAMES) {
          if (stringArray[0].equals(elementName)) {
            if (prop.isNegated()) {
              return "Property block stands for characters not matching " + stringArray[1];
            } else {
              return "Property block stands for " + "" + stringArray[1];
            }
          }
        }
      }
    }
    return null;
  }

  @Override
  public PsiElement getDocumentationElementForLookupItem(PsiManager psiManager, Object object, PsiElement element) {
    /*
    if (element instanceof RegExpProperty) {
      final String s;
      if (object instanceof PresentableLookupValue) {
        s = ((PresentableLookupValue)object).getPresentation();
      } else if (object instanceof String) {
        s = (String)object;
      } else {
        return null;
      }
      final Project project = element.getProject();
      final JavaPsiFacade f = JavaPsiFacade.getInstance(project);
      if (s.startsWith("java")) {
        final PsiClass charClass = f.findClass("java.lang.Character", GlobalSearchScope.allScope(project));
        if (charClass != null) {
          final PsiMethod[] methods = charClass.findMethodsByName("is" + s.substring("java".length()), false);
          return methods.length > 0 ? methods[0] : null;
        }
      } else if (s.matches("In[\\p{Upper}_]+")) {
        final PsiClass charClass = f.findClass("java.lang.Character.UnicodeBlock", GlobalSearchScope.allScope(project));
        if (charClass != null) {
          return charClass.findFieldByName(s.substring(2), false);
        }
      } else {
        return f.findClass("java.util.regex.Pattern", GlobalSearchScope.allScope(project));
      }
    }
      */
    return super.getDocumentationElementForLookupItem(psiManager, object, element);
  }

  @Nullable
  public String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
    if (element instanceof RegExpGroup) {
      return "Capturing Group: " + ((RegExpElement)element).getUnescapedText();
    } else {
      return null;
    }
  }
}

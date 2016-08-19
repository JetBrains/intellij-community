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
package com.intellij.util.xml.converters;

import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceProvider;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.converters.values.ClassArrayConverter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * User: Sergey.Vasiliev
 */
public class ClassArrayConverterImpl extends ClassArrayConverter {
  private static final JavaClassReferenceProvider REFERENCE_PROVIDER = new JavaClassReferenceProvider();

  static {
    REFERENCE_PROVIDER.setSoft(true);
    REFERENCE_PROVIDER.setAllowEmpty(true);
  }

  @NotNull
  public PsiReference[] createReferences(final GenericDomValue genericDomValue, final PsiElement element, final ConvertContext context) {
    final String s = genericDomValue.getStringValue();
    if (s != null) {
      final int offset = ElementManipulators.getOffsetInElement(element);
      final ArrayList<PsiReference> list = new ArrayList<>();
      int pos = -1;
      while (true) {
        while (pos + 1 < s.length()) {
          if (!Character.isWhitespace(s.charAt(pos + 1))) {
            break;
          }
          pos++;
        }
        int nextPos = s.indexOf(',', pos + 1);
        if (nextPos == -1) {
          createReference(element, s.substring(pos + 1), pos + 1 + offset, list);
          break;
        }
        else {
          createReference(element, s.substring(pos + 1, nextPos), pos + 1 + offset, list);
          pos = nextPos;
        }
      }
      return list.toArray(new PsiReference[list.size()]);
    }
    return PsiReference.EMPTY_ARRAY;
  }

  private static void createReference(final PsiElement element, final String s, final int offset, List<PsiReference> list) {
    final PsiReference[] references = REFERENCE_PROVIDER.getReferencesByString(s, element, offset);
    //noinspection ManualArrayToCollectionCopy
    for (PsiReference ref: references) {
      list.add(ref);
    }
  }
}

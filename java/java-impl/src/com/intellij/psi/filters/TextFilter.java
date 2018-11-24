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
package com.intellij.psi.filters;

import com.intellij.psi.PsiType;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NonNls;

public class TextFilter extends PlainTextFilter {

  public TextFilter(@NonNls String value){
    super(value);
  }

  public TextFilter(@NonNls String... values){
    super(values);
  }

  public TextFilter(@NonNls String value1, @NonNls String value2){
    super(value1, value2);
  }

  @Override
  protected String getTextByElement(final Object element) {
    if (element instanceof XmlTag) {
      return ((XmlTag)element).getLocalName();      
    }
    else if (element instanceof PsiType) {
      return ((PsiType) element).getPresentableText();
    }
    else {
      return super.getTextByElement(element);
    }
  }
}

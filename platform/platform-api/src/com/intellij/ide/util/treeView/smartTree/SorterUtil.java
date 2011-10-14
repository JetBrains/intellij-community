/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.ide.util.treeView.smartTree;

/**
 * @author Konstantin Bulenkov
 */
public class SorterUtil {
  private SorterUtil() {
  }

  public static String getStringPresentation(Object object) {
    String result = null;
    if (object instanceof SortableTreeElement) {
      result = ((SortableTreeElement) object).getAlphaSortKey();
    } else if (object instanceof TreeElement){
      result =  ((TreeElement)object).getPresentation().getPresentableText();
    } else if (object instanceof Group){
      result = ((Group)object).getPresentation().getPresentableText();
    }

    return result != null ? result : "";
  }
}

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

package com.intellij.ide.impl.dataRules;

import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public class PsiElementFromSelectionsRule implements GetDataRule {
  private static final Logger LOG = Logger.getInstance(PsiElementFromSelectionsRule.class);

  @Override
  public Object getData(@NotNull DataProvider dataProvider) {
    Object data = dataProvider.getData(PlatformDataKeys.SELECTED_ITEMS.getName());
    if (data == null) return null;

    if (!(data instanceof Object[])) {
      String errorMessage = "Value for data key 'PlatformDataKeys.SELECTED_ITEMS' must be of type Object[], but " + data.getClass() +
                            " is returned by " + dataProvider.getClass();
      LOG.error(PluginManagerCore.createPluginException(errorMessage, null, dataProvider.getClass()));
      return null;
    }

    final Object[] objects = (Object[])data;
    final PsiElement[] elements = new PsiElement[objects.length];
    for (int i = 0, objectsLength = objects.length; i < objectsLength; i++) {
      Object object = objects[i];
      if (!(object instanceof PsiElement)) return null;
      if (!((PsiElement)object).isValid()) return null;
      elements[i] = (PsiElement)object;
    }

    return elements;
  }
}

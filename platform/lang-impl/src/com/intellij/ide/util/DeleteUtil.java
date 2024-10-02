// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.ElementDescriptionUtil;
import com.intellij.psi.PsiDirectoryContainer;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.FactoryMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;

@ApiStatus.Internal
public final class DeleteUtil {
  private DeleteUtil() { }

  public static @NlsContexts.DialogMessage String generateWarningMessage(@PropertyKey(resourceBundle = IdeBundle.BUNDLE) String key,
                                                                         PsiElement @NotNull [] elements) {
    if (elements.length == 1) {
      String name = ElementDescriptionUtil.getElementDescription(elements[0], DeleteNameDescriptionLocation.INSTANCE);
      String type = ElementDescriptionUtil.getElementDescription(elements[0], DeleteTypeDescriptionLocation.SINGULAR);
      return IdeBundle.message(key, type + (StringUtil.isEmptyOrSpaces(name) ? "" : " \"" + name + '"'));
    }

    Map<String, Integer> countMap = FactoryMap.create(k -> 0);
    Map<String, String> pluralToSingular = new HashMap<>();
    int directoryCount = 0;
    String containerType = null;
    for (PsiElement elementToDelete : elements) {
      String type = ElementDescriptionUtil.getElementDescription(elementToDelete, DeleteTypeDescriptionLocation.PLURAL);
      pluralToSingular.put(type, ElementDescriptionUtil.getElementDescription(elementToDelete, DeleteTypeDescriptionLocation.SINGULAR));
      int oldCount = countMap.get(type).intValue();
      countMap.put(type, oldCount+1);
      if (elementToDelete instanceof PsiDirectoryContainer) {
        containerType = type;
        directoryCount += ((PsiDirectoryContainer) elementToDelete).getDirectories().length;
      }
    }

    StringBuilder buffer = new StringBuilder();
    for (Map.Entry<String, Integer> entry : countMap.entrySet()) {
      if (buffer.length() > 0) {
        buffer.append(" ").append(IdeBundle.message("prompt.delete.and")).append(" ");
      }

      int count = entry.getValue().intValue();
      buffer.append(count).append(" ");
      if (count == 1) {
        buffer.append(pluralToSingular.get(entry.getKey()));
      }
      else {
        buffer.append(entry.getKey());
      }

      if (entry.getKey().equals(containerType)) {
        buffer.append(" ").append(IdeBundle.message("prompt.delete.directory.paren", directoryCount));
      }
    }

    return IdeBundle.message(key, buffer.toString());
  }
}

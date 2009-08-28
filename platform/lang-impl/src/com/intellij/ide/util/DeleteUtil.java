package com.intellij.ide.util;

import com.intellij.ide.IdeBundle;
import com.intellij.psi.ElementDescriptionUtil;
import com.intellij.psi.PsiDirectoryContainer;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NonNls;

import java.text.MessageFormat;
import java.util.Map;

/**
 * @author dsl
 */
public class DeleteUtil {
  private DeleteUtil() {}

  public static void appendMessage(int count, @NonNls String propertyKey, StringBuffer buffer) {
    if (count > 0) {
      if (buffer.length() > 0) {
        buffer.append(" ").append(IdeBundle.message("prompt.delete.and")).append(" ");
      }
      buffer.append(count);
      buffer.append(' ');
      buffer.append(IdeBundle.message(propertyKey, count));
    }
  }

  public static String generateWarningMessage(String messageTemplate, final PsiElement[] elements) {
    if (elements.length == 1) {
      String name = ElementDescriptionUtil.getElementDescription(elements [0], DeleteNameDescriptionLocation.INSTANCE);
      String type = ElementDescriptionUtil.getElementDescription(elements [0], DeleteTypeDescriptionLocation.SINGULAR);
      return MessageFormat.format(messageTemplate, type + " \"" + name + "\"");
    }

    FactoryMap<String, Integer> countMap = new FactoryMap<String, Integer>() {
      protected Integer create(final String key) {
        return 0;
      }
    };
    Map<String, String> pluralToSingular = new HashMap<String, String>();
    int directoryCount = 0;
    String containerType = null;

    for (final PsiElement elementToDelete : elements) {
      String type = ElementDescriptionUtil.getElementDescription(elementToDelete, DeleteTypeDescriptionLocation.PLURAL);
      pluralToSingular.put(type, ElementDescriptionUtil.getElementDescription(elementToDelete, DeleteTypeDescriptionLocation.SINGULAR));
      int oldCount = countMap.get(type).intValue();
      countMap.put(type, oldCount+1);
      if (elementToDelete instanceof PsiDirectoryContainer) {
        containerType = type;
        directoryCount += ((PsiDirectoryContainer) elementToDelete).getDirectories().length;
      }
    }

    StringBuffer buffer = new StringBuffer();
    for (Map.Entry<String, Integer> entry : countMap.entrySet()) {
      if (buffer.length() > 0) {
        if (buffer.length() > 0) {
          buffer.append(" ").append(IdeBundle.message("prompt.delete.and")).append(" ");
        }
      }
      final int count = entry.getValue().intValue();

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
    return MessageFormat.format(messageTemplate, buffer.toString()); 
  }
}

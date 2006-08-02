package com.intellij.lang.properties.psi;

import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiManager;
import com.intellij.lang.StdLanguages;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author cdr
 */
public class PropertiesElementFactory {
  @NotNull
  public static Property createProperty(@NotNull Project project, @NonNls @NotNull String name, @NonNls @NotNull String value) {
    String text = escape(name) + "=" + value;
    final PropertiesFile dummyFile = createPropertiesFile(project, text);
    return dummyFile.getProperties().get(0);
  }

  @NotNull
  public static PropertiesFile createPropertiesFile(@NotNull Project project, @NonNls @NotNull String text) {
    @NonNls String filename = "dummy." + StdFileTypes.PROPERTIES.getDefaultExtension();
    return (PropertiesFile)PsiManager.getInstance(project).getElementFactory().createFileFromText(filename, StdLanguages.PROPERTIES.getAssociatedFileType(), text);
  }

  @NotNull
  private static String escape(@NotNull String name) {
    if (StringUtil.startsWithChar(name, '#')) {
      name = escapeChar(name, '#');
    }
    if (StringUtil.startsWithChar(name, '!')) {
      name = escapeChar(name, '!');
    }
    name = escapeChar(name, '=');
    name = escapeChar(name, ':');
    name = escapeChar(name, ' ');
    name = escapeChar(name, '\t');
    return name;
  }

  @NotNull
  private static String escapeChar(@NotNull String name, char c) {
    int offset = 0;
    while (true) {
      int i = name.indexOf(c, offset);
      if (i == -1) return name;
      if (i == 0 || name.charAt(i - 1) != '\\') {
        name = name.substring(0, i) + '\\' + name.substring(i);
      }
      offset = i + 2;
    }
  }
}

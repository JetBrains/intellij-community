/*
 * @author max
 */
package com.intellij.lang;

import com.intellij.lang.refactoring.NamesValidator;
import com.intellij.openapi.project.Project;

public class LanguageNamesValidation extends LanguageExtension<NamesValidator> {
  public static final LanguageNamesValidation INSTANCE = new LanguageNamesValidation();

  private LanguageNamesValidation() {
    super("com.intellij.lang.namesValidator", new DefaultNamesValidator());
  }
  
  protected static class DefaultNamesValidator implements NamesValidator {
    public boolean isIdentifier(final String name, final Project project) {
      final int len = name.length();
      if (len == 0) return false;

      if (!Character.isJavaIdentifierStart(name.charAt(0))) return false;

      for (int i = 1; i < len; i++) {
        if (!Character.isJavaIdentifierPart(name.charAt(i))) return false;
      }

      return true;
    }

    public boolean isKeyword(final String name, final Project project) {
      return false;
    }
  }
}
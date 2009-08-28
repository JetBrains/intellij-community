/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.lookup;

/**
 * What to do if there's only one element in completion lookup? Should IDEA show lookup or just insert this element? It'll
 * ask the element itself, using its {@link LookupElement#getAutoCompletionPolicy()} method.
 *
 * @author peter
 */
public enum AutoCompletionPolicy {
  /**
   * Self-explaining
   */
  NEVER_AUTOCOMPLETE,

  /**
   * If 'auto-complete if only one choice' is configured in settings, the item will be inserted, otherwise - no.
   */
  SETTINGS_DEPENDENT,

  /**
   * If caret is positioned inside an identifier, and 'auto-complete if only one choice' is configured in settings,
   * a lookup with one item will still open, giving user a chance to overwrite the identifier using Tab key
   */
  GIVE_CHANCE_TO_OVERWRITE,

  /**
   * Self-explaining
   */
  ALWAYS_AUTOCOMPLETE
}

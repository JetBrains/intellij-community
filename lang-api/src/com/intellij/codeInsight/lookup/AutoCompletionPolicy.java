/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.lookup;

/**
 * @author peter
 */
public enum AutoCompletionPolicy {
  NEVER_AUTOCOMPLETE,
  SETTINGS_DEPENDENT,
  GIVE_CHANCE_TO_OVERWRITE,
  ALWAYS_AUTOCOMPLETE
}

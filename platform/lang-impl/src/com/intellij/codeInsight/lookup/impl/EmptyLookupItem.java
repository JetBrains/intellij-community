/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.lookup.impl;

import com.intellij.codeInsight.lookup.LookupItem;

/**
 * @author peter
*/
class EmptyLookupItem extends LookupItem<String> {
  public EmptyLookupItem(final String s) {
    super(s, "                       ");
  }
}

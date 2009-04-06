/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.lookup;

import com.intellij.util.Consumer;
import com.intellij.openapi.extensions.ExtensionPointName;

/**
 * @author peter
 */
public interface LookupActionProvider {
  ExtensionPointName<LookupActionProvider> EP_NAME = ExtensionPointName.create("com.intellij.lookup.actionProvider");

  void fillActions(LookupElement element, Lookup lookup, Consumer<LookupElementAction> consumer);

}

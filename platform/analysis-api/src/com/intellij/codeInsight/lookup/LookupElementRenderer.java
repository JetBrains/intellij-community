// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.lookup;

public abstract class LookupElementRenderer<T extends LookupElement> {
  public abstract void renderElement(T element, LookupElementPresentation presentation);
}

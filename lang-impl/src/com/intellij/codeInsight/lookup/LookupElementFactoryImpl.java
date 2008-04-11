/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.codeInsight.lookup;

import com.intellij.codeInsight.completion.simple.SimpleLookupItem;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiNamedElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class LookupElementFactoryImpl extends LookupElementFactory{

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass"})
  @NotNull
  public static LookupElementFactoryImpl getInstance() {
    return (LookupElementFactoryImpl)LookupElementFactory.getInstance();
  }


  public SimpleLookupItem<String> createLookupElement(@NotNull String lookupString) {
    return new SimpleLookupItem<String>(lookupString, lookupString);
  }

  public <T extends PsiNamedElement> SimpleLookupItem<T> createLookupElement(@NotNull T element) {
    return createLookupElement(element, StringUtil.notNullize(element.getName()));
  }

  public <T> SimpleLookupItem<T> createLookupElement(@NotNull T element, @NotNull String lookupString) {
    return new SimpleLookupItem<T>(element, lookupString);
  }
}

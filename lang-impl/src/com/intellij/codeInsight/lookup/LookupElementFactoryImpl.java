/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.codeInsight.lookup;

import com.intellij.codeInsight.completion.simple.SimpleLookupItem;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.psi.meta.PsiPresentableMetaData;
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
    final SimpleLookupItem<T> lookupItem = createLookupElement(element, StringUtil.notNullize(element.getName()));
    if (element instanceof PsiMetaOwner) {
      final PsiMetaData metaData = ((PsiMetaOwner)element).getMetaData();
      if (metaData instanceof PsiPresentableMetaData) {
        final PsiPresentableMetaData presentableMetaData = (PsiPresentableMetaData)metaData;
        lookupItem.setIcon(presentableMetaData.getIcon());
        final String name = presentableMetaData.getName();
        if (StringUtil.isNotEmpty(name)) {
          lookupItem.setPresentableText(name);
        }
      }
    }
    return lookupItem;
  }

  public <T> SimpleLookupItem<T> createLookupElement(@NotNull T element, @NotNull String lookupString) {
    return new SimpleLookupItem<T>(element, lookupString);
  }
}

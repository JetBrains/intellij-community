// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.impl.ElementLookupRenderer;
import com.intellij.codeInsight.template.Template;
import org.jetbrains.annotations.ApiStatus;


@ApiStatus.Internal
public final class TemplateLookupRenderer implements ElementLookupRenderer<Template> {
  @Override
  public boolean handlesItem(final Object element) {
    return element instanceof Template;
  }

  @Override
  public void renderElement(final LookupItem item, final Template element, final LookupElementPresentation presentation) {
    presentation.setItemText(element.getKey());
    presentation.setTypeText(element.getDescription());
  }

}

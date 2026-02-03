// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.navigation;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;


public class DelegatingItemPresentation implements ColoredItemPresentation {
  private final ItemPresentation myBase;
  private @Nls String myPresentableText;
  private String myLocationString;
  private Icon myIcon;
  private boolean myCustomLocationString;
  
  public DelegatingItemPresentation(ItemPresentation base) {
    myBase = base;
  }

  public DelegatingItemPresentation withPresentableText(@Nls String presentableText) {
    myPresentableText = presentableText;
    return this;
  }

  public DelegatingItemPresentation withLocationString(@Nullable String locationString) {
    myCustomLocationString = true;
    myLocationString = locationString;
    return this;
  }

  public DelegatingItemPresentation withIcon(Icon icon) {
    myIcon = icon;
    return this;
  }

  @Override
  public String getPresentableText() {
    if (myPresentableText != null) {
      return myPresentableText;
    }
    return myBase.getPresentableText();
  }

  @Override
  public String getLocationString() {
    if (myCustomLocationString) {
      return myLocationString;
    }
    return myBase.getLocationString();
  }

  @Override
  public Icon getIcon(boolean open) {
    if (myIcon != null) {
      return myIcon;
    }
    return myBase.getIcon(open);
  }

  @Override
  public TextAttributesKey getTextAttributesKey() {
    return myBase instanceof ColoredItemPresentation ? ((ColoredItemPresentation) myBase).getTextAttributesKey() : null;
  }
}

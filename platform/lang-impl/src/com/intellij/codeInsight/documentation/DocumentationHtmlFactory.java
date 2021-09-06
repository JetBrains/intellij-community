// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.documentation;

import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleTypeManager;
import com.intellij.util.ui.JBHtmlEditorKit;
import org.jetbrains.annotations.NotNull;

import javax.swing.text.Element;
import javax.swing.text.View;
import javax.swing.text.html.ImageView;
import java.awt.*;

final class DocumentationHtmlFactory extends JBHtmlEditorKit.JBHtmlFactory {

  private final @NotNull Component myReferenceComponent;

  DocumentationHtmlFactory(@NotNull Component referenceComponent) {
    myReferenceComponent = referenceComponent;
    setAdditionalIconResolver(src -> {
      ModuleType<?> id = ModuleTypeManager.getInstance().findByID(src);
      return id == null ? null : id.getIcon();
    });
  }

  @Override
  public View create(Element elem) {
    View view = super.create(elem);
    if (view instanceof ImageView) {
      // we have to work with raw image, apply scaling manually
      return new DocumentationScalingImageView(elem, myReferenceComponent);
    }
    return view;
  }
}

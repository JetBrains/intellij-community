// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.documentation;

import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleTypeManager;
import com.intellij.openapi.module.UnknownModuleType;
import com.intellij.util.ui.JBHtmlEditorKit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.Element;
import javax.swing.text.View;
import javax.swing.text.html.ImageView;
import java.awt.*;

final class DocumentationHtmlFactory extends JBHtmlEditorKit.JBHtmlFactory {

  private final @NotNull Component myReferenceComponent;

  DocumentationHtmlFactory(@NotNull Component referenceComponent) {
    myReferenceComponent = referenceComponent;
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

  @Override
  protected @Nullable Icon getIcon(@NotNull String src) {
    Icon icon = moduleIcon(src);
    if (icon != null) {
      return icon;
    }
    return super.getIcon(src);
  }

  private static @Nullable Icon moduleIcon(@NotNull String src) {
    ModuleType<?> moduleType = ModuleTypeManager.getInstance().findByID(src);
    return moduleType == null || moduleType instanceof UnknownModuleType
           ? null
           : moduleType.getIcon();
  }
}

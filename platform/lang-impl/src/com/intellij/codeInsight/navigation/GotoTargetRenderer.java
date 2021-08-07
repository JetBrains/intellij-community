// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.navigation;

import com.intellij.navigation.TargetPresentation;
import com.intellij.ui.list.TargetPopup;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.function.Function;

final class GotoTargetRenderer implements ListCellRenderer<Object> {

  private final ListCellRenderer<Object> myNullRenderer = new DefaultListCellRenderer();
  private final ListCellRenderer<Object> myActionRenderer = new GotoTargetActionRenderer();
  private final ListCellRenderer<Object> myPresentationRenderer;

  GotoTargetRenderer(@NotNull Function<@NotNull ? super Object, @NotNull ? extends TargetPresentation> presentationProvider) {
    myPresentationRenderer = TargetPopup.createTargetPresentationRenderer(presentationProvider);
  }

  @Override
  public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
    if (value == null) {
      return myNullRenderer.getListCellRendererComponent(list, null, index, isSelected, cellHasFocus);
    }
    else if (value instanceof GotoTargetHandler.AdditionalAction) {
      return myActionRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
    }
    else {
      return myPresentationRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
    }
  }
}

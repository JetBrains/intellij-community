/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.wm;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.util.Consumer;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.event.MouseEvent;

public interface StatusBarWidget extends Disposable {
  enum PlatformType {
    DEFAULT, MAC
  }

  @NotNull
  String ID();

  @Nullable
  WidgetPresentation getPresentation(@NotNull PlatformType type);

  void install(@NotNull final StatusBar statusBar);

  interface Multiframe extends StatusBarWidget {
    StatusBarWidget copy();
  }

  interface WidgetPresentation {
    @Nullable
    String getTooltipText();

    @Nullable
    Consumer<MouseEvent> getClickConsumer();
  }

  interface IconPresentation extends WidgetPresentation {
    @NotNull
    Icon getIcon();
  }

  interface TextPresentation extends WidgetPresentation {
    @NotNull
    String getText();

    @NotNull
    @Deprecated
    default String getMaxPossibleText() { return ""; };

    float getAlignment();
  }

  interface MultipleTextValuesPresentation extends WidgetPresentation {
    @Nullable("null means the widget is unable to show the popup")
    ListPopup getPopupStep();

    @Nullable
    String getSelectedValue();

    @NotNull
    @Deprecated
    default String getMaxValue() { return ""; }
  }

  abstract class WidgetBorder implements Border {
    public static final Border INSTANCE = JBUI.Borders.empty(0, 2);
    public static final Border WIDE = JBUI.Borders.empty(0, 4);
  }
}

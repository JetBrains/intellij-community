// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.colorpicker;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.util.Ref;
import com.intellij.ui.BalloonImpl;
import com.intellij.ui.ColorPicker;
import com.intellij.ui.ColorPickerPopupProvider;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.picker.ColorListener;
import com.intellij.ui.picker.ColorPickerPopupCloseListener;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import java.awt.Color;
import java.awt.MouseInfo;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.List;

@ApiStatus.Internal
public final class ColorPickerPopupProviderImpl implements ColorPickerPopupProvider {
  @Override
  public void showPopup(@Nullable Project project,
                        @Nullable Color currentColor,
                        @NotNull ColorListener listener,
                        @Nullable RelativePoint location,
                        boolean showAlpha,
                        boolean showAlphaAsPercent,
                        @Nullable ColorPickerPopupCloseListener popupCloseListener) {
    Ref<LightCalloutPopup> ref = Ref.create();

    ColorListener colorListener = new ColorListener() {
      final Object groupId = new Object();
      final Alarm alarm = new Alarm();

      @Override
      public void colorChanged(final Color color, final Object source) {
        Runnable apply = () -> CommandProcessor.getInstance().executeCommand(project,
                                                                             () -> listener.colorChanged(color, source),
                                                                             IdeBundle.message("command.name.apply.color"),
                                                                             groupId);
        alarm.cancelAllRequests();
        Runnable request = () -> ApplicationManager.getApplication().invokeLaterOnWriteThread(apply);
        if (source instanceof ColorPipetteButton && ((ColorPipetteButton)source).getCurrentState() == ColorPipetteButton.PipetteState.UPDATING) {
          alarm.addRequest(request, 150);
        } else {
          request.run();
        }
      }
    };

    List<Color> recentColors = ColorPicker.getRecentColors();
    ColorPickerBuilder builder = new ColorPickerBuilder(showAlpha, showAlphaAsPercent)
      .setOriginalColor(currentColor)
      .addSaturationBrightnessComponent()
      .addColorAdjustPanel(new MaterialGraphicalColorPipetteProvider())
      .addColorValuePanel().withFocus();
    if (!recentColors.isEmpty()) {
      builder/*.addSeparator()*/
        .addCustomComponent(new ColorPickerComponentProvider() {
          @Override
          public @NotNull JComponent createComponent(@NotNull ColorPickerModel colorPickerModel) {
            return new RecentColorsPalette(colorPickerModel, recentColors);
          }
        });
    }
      builder.addColorListener(colorListener,true)
      .addColorListener(new ColorListener() {
        @Override
        public void colorChanged(Color color, Object source) {
          updatePointer(ref);
        }
      }, true)
        .addColorListener(new ColorListener() {
          @Override
          public void colorChanged(Color color, Object source) {
            ColorPicker.saveRecentColor(color, recentColors);
          }
        }, false)
      .focusWhenDisplay(true)
      .setFocusCycleRoot(true)
      .addKeyAction(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), cancelPopup(ref))
      .addKeyAction(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), applyColor(ref))
      .setPopupCloseListener(popupCloseListener);
    LightCalloutPopup popup = builder.build();
    ref.set(popup);

    if (location == null) {
      location = new RelativePoint(MouseInfo.getPointerInfo().getLocation());
    }
    popup.show(location.getScreenPoint());
    updatePointer(ref);
  }

  private static void updatePointer(Ref<LightCalloutPopup> ref) {
    LightCalloutPopup popup = ref.get();
    Balloon balloon = popup.getBalloon();
    if (balloon instanceof BalloonImpl) {
      RelativePoint showingPoint = ((BalloonImpl)balloon).getShowingPoint();
      Color c = popup.getPointerColor(showingPoint, ((BalloonImpl)balloon).getComponent());
      if (c != null) {
        c = ColorUtil.withAlpha(c, 1.0); //clear transparency
      }
      ((BalloonImpl)balloon).setPointerColor(c);
    }
  }

  private static @NotNull AbstractAction cancelPopup(Ref<LightCalloutPopup> ref) {
    return new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final LightCalloutPopup popup = ref.get();
        if (popup != null) {
          popup.cancel();
        }
      }
    };
  }

  private static @NotNull AbstractAction applyColor(Ref<LightCalloutPopup> ref) {
    return new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final LightCalloutPopup popup = ref.get();
        if (popup != null) {
          popup.close();
        }
      }
    };
  }
}

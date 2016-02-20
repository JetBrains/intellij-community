/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.ui.components;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.ui.mac.foundation.ID;
import com.intellij.util.Alarm;
import com.intellij.util.Producer;
import com.intellij.util.ui.RegionPainter;
import com.intellij.util.ui.UIUtil;
import com.sun.jna.Callback;
import com.sun.jna.Pointer;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.intellij.ui.mac.foundation.Foundation.*;

/**
 * @author Sergey.Malenkov
 */
final class MacScrollBarUI extends DefaultScrollBarUI {
  private static final RegistryValue DISABLED = Registry.get("ide.mac.disableMacScrollbars");
  private static final List<MacScrollBarUI> UI = Collections.synchronizedList(new ArrayList<MacScrollBarUI>());
  private final Alarm myAlarm = new Alarm();
  private boolean myTrackHovered;

  MacScrollBarUI() {
    super(14, 15, 11);
  }

  @Override
  boolean isAbsolutePositioning(MouseEvent event) {
    return Behavior.JumpToSpot == Behavior.CURRENT.get();
  }

  @Override
  boolean isBorderNeeded(JComponent c) {
    return !c.isOpaque() && myTrackAnimator.myValue > 0 && myThumbAnimator.myValue > 0;
  }

  @Override
  boolean isTrackClickable() {
    return myScrollBar.isOpaque() || (myTrackAnimator.myValue > 0 && myThumbAnimator.myValue > 0);
  }

  @Override
  boolean isTrackExpandable() {
    return true;
  }

  @Override
  void onTrackHover(boolean hover) {
    myTrackHovered = hover;
    myTrackAnimator.start(hover);
    if (!hover || myScrollBar != null && myScrollBar.isOpaque()) {
      myThumbAnimator.start(hover);
    }
  }

  @Override
  void onThumbHover(boolean hover) {
  }

  @Override
  void paintTrack(Graphics2D g, int x, int y, int width, int height, JComponent c) {
    if (isBorderNeeded(c)) super.paintTrack(g, x, y, width, height, c);
  }

  @Override
  void paintThumb(Graphics2D g, int x, int y, int width, int height, JComponent c) {
    RegionPainter<Float> p = isDark(c) ? JBScrollPane.MAC_THUMB_DARK_PAINTER : JBScrollPane.MAC_THUMB_PAINTER;
    if (c.isOpaque()) {
      paint(p, g, x, y, width, height, c, myThumbAnimator.myValue, true);
    }
    else if (myThumbAnimator.myValue > 0) {
      paint(p, g, x, y, width, height, c, myThumbAnimator.myValue, false);
    }
  }

  @Override
  void onThumbMove() {
    if (myScrollBar != null && myScrollBar.isShowing() && !myScrollBar.isOpaque()) {
      myThumbAnimator.rewind(true);
      myAlarm.cancelAllRequests();
      if (!myTrackHovered) {
        myAlarm.addRequest(new Runnable() {
          @Override
          public void run() {
            myThumbAnimator.start(false);
          }
        }, 500);
      }
    }
  }

  @Override
  public void installUI(JComponent c) {
    super.installUI(c);
    updateStyle(Style.CURRENT.get());
    UI.add(this);
  }

  @Override
  public void uninstallUI(JComponent c) {
    UI.remove(this);
    myAlarm.cancelAllRequests();
    super.uninstallUI(c);
  }

  private void updateStyle(Style style) {
    if (myScrollBar != null) {
      myScrollBar.setOpaque(style != Style.Overlay);
      myScrollBar.revalidate();
      myScrollBar.repaint();
      onThumbMove();
    }
  }

  private static ID createDelegate(String name, Pointer pointer, Callback callback) {
    ID delegateClass = allocateObjcClassPair(getObjcClass("NSObject"), name);
    if (!ID.NIL.equals(delegateClass)) {
      if (!addMethod(delegateClass, pointer, callback, "v@")) {
        throw new RuntimeException("Cannot add observer method");
      }
      registerObjcClassPair(delegateClass);
    }
    return invoke(name, "new");
  }

  private static <T> T callMac(Producer<T> producer) {
    if (SystemInfo.isMac) {
      NSAutoreleasePool pool = new NSAutoreleasePool();
      try {
        return producer.produce();
      }
      catch (Throwable throwable) {
        Logger.getInstance(MacScrollBarUI.class).warn(throwable);
      }
      finally {
        pool.drain();
      }
    }
    return null;
  }

  static {
    callMac(Behavior.INIT);
    callMac(Style.INIT);
  }

  private enum Behavior {
    NextPage, JumpToSpot;

    private static final Native<Behavior> CURRENT = new Native<Behavior>() {
      @Nullable
      @Override
      public Behavior produce() {
        ID defaults = invoke("NSUserDefaults", "standardUserDefaults");
        invoke(defaults, "synchronize");
        ID behavior = invoke(defaults, "boolForKey:", nsString("AppleScrollerPagingBehavior"));
        return 1 == behavior.intValue() ? JumpToSpot : NextPage;
      }
    };
    private static final Producer<ID> INIT = new Producer<ID>() {
      @Nullable
      @Override
      public ID produce() {
        return invoke(invoke("NSDistributedNotificationCenter", "defaultCenter"),
                      "addObserver:selector:name:object:",
                      createDelegate("JBScrollBarBehaviorObserver", createSelector("handleBehaviorChanged:"), CURRENT),
                      createSelector("handleBehaviorChanged:"),
                      nsString("AppleNoRedisplayAppearancePreferenceChanged"),
                      ID.NIL,
                      2 // NSNotificationSuspensionBehaviorCoalesce
        );
      }
    };
  }

  private enum Style {
    Legacy, Overlay;

    private static final Native<Style> CURRENT = new Native<Style>() {
      @Override
      public void run() {
        Style oldStyle = get();
        if (!DISABLED.asBoolean() && SystemInfo.isMacOSMountainLion) super.run();
        Style newStyle = get();
        if (newStyle != oldStyle) {
          for (MacScrollBarUI ui : UI.toArray(new MacScrollBarUI[0])) {
            ui.updateStyle(newStyle);
          }
        }
      }

      @Nullable
      @Override
      public Style produce() {
        ID style = invoke(getObjcClass("NSScroller"), "preferredScrollerStyle");
        return 1 == style.intValue() ? Overlay : Legacy;
      }
    };
    private static final Producer<ID> INIT = new Producer<ID>() {
      @Nullable
      @Override
      public ID produce() {
        return invoke(invoke("NSNotificationCenter", "defaultCenter"),
                      "addObserver:selector:name:object:",
                      createDelegate("JBScrollBarStyleObserver", createSelector("handleScrollerStyleChanged:"), CURRENT),
                      createSelector("handleScrollerStyleChanged:"),
                      nsString("NSPreferredScrollerStyleDidChangeNotification"),
                      ID.NIL
        );
      }
    };
  }

  private static abstract class Native<T> implements Callback, Runnable, Producer<T> {
    private T myValue;

    public Native() {
      UIUtil.invokeLaterIfNeeded(this);
    }

    T get() {
      return myValue;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void callback(ID self, Pointer selector, ID event) {
      UIUtil.invokeLaterIfNeeded(this);
    }

    @Override
    public void run() {
      myValue = callMac(this);
    }
  }
}
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
import java.awt.event.AWTEventListener;
import java.awt.event.MouseEvent;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.intellij.ui.mac.foundation.Foundation.*;

/**
 * @author Sergey.Malenkov
 */
final class MacScrollBarUI extends DefaultScrollBarUI {
  private static final RegistryValue DISABLED = Registry.get("ide.mac.disableMacScrollbars");
  private static final List<Reference<MacScrollBarUI>> UI = new ArrayList<>();
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
    if (myScrollBar != null && myScrollBar.isOpaque()) {
      myTrackAnimator.start(hover);
      myThumbAnimator.start(hover);
    }
    else if (hover) {
      myTrackAnimator.start(true);
    }
    else {
      myThumbAnimator.start(false);
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
    if (c.isOpaque()) {
      RegionPainter<Float> p = ScrollColorProducer.isDark(c) ? ScrollPainter.Thumb.Mac.DARCULA : ScrollPainter.Thumb.Mac.DEFAULT;
      paint(p, g, x, y, width, height, c, myThumbAnimator.myValue, true);
    }
    else if (myThumbAnimator.myValue > 0) {
      RegionPainter<Float> p = ScrollColorProducer.isDark(c) ? ScrollPainter.Thumb.Mac.Overlay.DARCULA : ScrollPainter.Thumb.Mac.Overlay.DEFAULT;
      paint(p, g, x, y, width, height, c, myThumbAnimator.myValue, false);
    }
  }

  @Override
  void onThumbMove() {
    if (myScrollBar != null && myScrollBar.isShowing() && !myScrollBar.isOpaque()) {
      if (!myTrackHovered && myThumbAnimator.myValue == 0) myTrackAnimator.rewind(false);
      myThumbAnimator.rewind(true);
      myAlarm.cancelAllRequests();
      if (!myTrackHovered) {
        myAlarm.addRequest(() -> myThumbAnimator.start(false), 700);
      }
    }
  }

  @Override
  public void installUI(JComponent c) {
    super.installUI(c);
    updateStyle(Style.CURRENT.get());
    processReferences(this, null, null);
    AWTEventListener listener = MOVEMENT_LISTENER.getAndSet(null); // add only one movement listener
    if (listener != null) Toolkit.getDefaultToolkit().addAWTEventListener(listener, AWTEvent.MOUSE_MOTION_EVENT_MASK);
  }

  @Override
  public void uninstallUI(JComponent c) {
    processReferences(null, this, null);
    myAlarm.cancelAllRequests();
    super.uninstallUI(c);
  }

  /**
   * The movement listener that is intended to do not hide shown thumb while mouse is moving.
   */
  private static final AtomicReference<AWTEventListener> MOVEMENT_LISTENER = new AtomicReference<>(new AWTEventListener() {
    @Override
    public void eventDispatched(AWTEvent event) {
      if (event != null && MouseEvent.MOUSE_MOVED == event.getID()) {
        Object source = event.getSource();
        if (source instanceof Component) {
          JScrollPane pane = UIUtil.getParentOfType(JScrollPane.class, (Component)source);
          if (pane != null) {
            pauseThumbAnimation(pane.getHorizontalScrollBar());
            pauseThumbAnimation(pane.getVerticalScrollBar());
          }
        }
      }
    }

    /**
     * Pauses animation of the thumb if it is shown.
     *
     * @param bar the scroll bar with custom UI
     */
    private void pauseThumbAnimation(JScrollBar bar) {
      Object object = bar == null ? null : bar.getUI();
      if (object instanceof MacScrollBarUI) {
        MacScrollBarUI ui = (MacScrollBarUI)object;
        if (0 < ui.myThumbAnimator.myValue) ui.onThumbMove();
      }
    }
  });


  /**
   * Processes references in the static list of references synchronously.
   * This method removes all cleared references and the reference specified to remove,
   * collects objects from other references into the specified list and
   * adds the reference specified to add.
   *
   * @param toAdd    the object to add to the static list of references (ignored if {@code null})
   * @param toRemove the object to remove from the static list of references (ignored if {@code null})
   * @param list     the list to collect all available objects (ignored if {@code null})
   */
  private static void processReferences(MacScrollBarUI toAdd, MacScrollBarUI toRemove, List<MacScrollBarUI> list) {
    synchronized (UI) {
      Iterator<Reference<MacScrollBarUI>> iterator = UI.iterator();
      while (iterator.hasNext()) {
        Reference<MacScrollBarUI> reference = iterator.next();
        MacScrollBarUI ui = reference.get();
        if (ui == null || ui == toRemove) {
          iterator.remove();
        }
        else if (list != null) {
          list.add(ui);
        }
      }
      if (toAdd != null) {
        UI.add(new WeakReference<>(toAdd));
      }
    }
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
    private static final Producer<ID> INIT = () -> invoke(invoke("NSDistributedNotificationCenter", "defaultCenter"),
                                                      "addObserver:selector:name:object:",
                                                      createDelegate("JBScrollBarBehaviorObserver", createSelector("handleBehaviorChanged:"), CURRENT),
                                                      createSelector("handleBehaviorChanged:"),
                                                      nsString("AppleNoRedisplayAppearancePreferenceChanged"),
                                                      ID.NIL,
                                                      2 // NSNotificationSuspensionBehaviorCoalesce
    );
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
          List<MacScrollBarUI> list = new ArrayList<>();
          processReferences(null, null, list);
          for (MacScrollBarUI ui : list) {
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
    private static final Producer<ID> INIT = () -> invoke(invoke("NSNotificationCenter", "defaultCenter"),
                                                      "addObserver:selector:name:object:",
                                                      createDelegate("JBScrollBarStyleObserver", createSelector("handleScrollerStyleChanged:"), CURRENT),
                                                      createSelector("handleScrollerStyleChanged:"),
                                                      nsString("NSPreferredScrollerStyleDidChangeNotification"),
                                                      ID.NIL
    );
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
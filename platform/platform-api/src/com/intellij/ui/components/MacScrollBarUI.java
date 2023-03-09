// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.mac.foundation.ID;
import com.intellij.util.NotNullProducer;
import com.intellij.util.ui.EdtInvocationManager;
import com.sun.jna.Callback;
import com.sun.jna.Pointer;
import org.jetbrains.annotations.NotNull;

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

class MacScrollBarUI extends DefaultScrollBarUI {
  private static final List<Reference<MacScrollBarUI>> UI = new ArrayList<>();

  MacScrollBarUI(int thickness, int thicknessMax, int thicknessMin) {
    super(thickness, thicknessMax, thicknessMin);
  }

  MacScrollBarUI() {
    super(14, 14, 11);
  }

  @Override
  protected ScrollBarAnimationBehavior createBaseAnimationBehavior() {
    return new MacScrollBarAnimationBehavior(
      new Computable<>() {
        @Override
        public JScrollBar compute() {
          return myScrollBar;
        }
      },
      myTrack.animator,
      myThumb.animator);
  }

  @Override
  boolean isAbsolutePositioning(MouseEvent event) {
    return Behavior.JumpToSpot == Behavior.CURRENT.get();
  }

  @Override
  boolean isTrackClickable() {
    return isOpaque(myScrollBar) || (myAnimationBehavior.getTrackFrame() > 0 && myAnimationBehavior.getThumbFrame() > 0);
  }

  @Override
  boolean isTrackExpandable() {
    return !isOpaque(myScrollBar);
  }

  @Override
  void paintTrack(Graphics2D g, JComponent c) {
    if (myAnimationBehavior.getTrackFrame() > 0 && myAnimationBehavior.getThumbFrame() > 0 || isOpaque(c)) super.paintTrack(g, c);
  }

  @Override
  void paintThumb(Graphics2D g, JComponent c) {
    if (isOpaque(c)) {
      paint(myThumb, g, c, true);
    }
    else if (myAnimationBehavior.getThumbFrame() > 0) {
      paint(myThumb, g, c, false);
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
          JScrollPane pane = ComponentUtil.getParentOfType((Class<? extends JScrollPane>)JScrollPane.class, (Component)source);
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
    private static void pauseThumbAnimation(JScrollBar bar) {
      Object object = bar == null ? null : bar.getUI();
      if (object instanceof MacScrollBarUI) {
        MacScrollBarUI ui = (MacScrollBarUI)object;
        if (0 < ui.myAnimationBehavior.getThumbFrame()) ui.myAnimationBehavior.onThumbMove();
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
  private static void processReferences(MacScrollBarUI toAdd, MacScrollBarUI toRemove, List<? super MacScrollBarUI> list) {
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

  protected void updateStyle(Style style) {
    if (myScrollBar != null) {
      myScrollBar.setOpaque(style != Style.Overlay);
      myScrollBar.revalidate();
      myScrollBar.repaint();
      myAnimationBehavior.onThumbMove();
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

  private static <T> T callMac(NotNullProducer<? extends T> producer) {
    if (SystemInfoRt.isMac) {
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

  private enum Behavior {
    NextPage, JumpToSpot;

    private static final Native<Behavior> CURRENT = new Native<>() {
      @NotNull
      @Override
      public Behavior produce() {
        ID defaults = invoke("NSUserDefaults", "standardUserDefaults");
        invoke(defaults, "synchronize");
        ID behavior = invoke(defaults, "boolForKey:", nsString("AppleScrollerPagingBehavior"));
        Behavior value = 1 == behavior.intValue() ? JumpToSpot : NextPage;
        Logger.getInstance(MacScrollBarUI.class).debug("scroll bar behavior ", value, " from ", behavior);
        return value;
      }

      @Override
      public String toString() {
        return "scroll bar behavior";
      }

      @Override
      ID initialize() {
        return invoke(invoke("NSDistributedNotificationCenter", "defaultCenter"),
                      "addObserver:selector:name:object:",
                      createDelegate("JBScrollBarBehaviorObserver", createSelector("handleBehaviorChanged:"), this),
                      createSelector("handleBehaviorChanged:"),
                      nsString("AppleNoRedisplayAppearancePreferenceChanged"),
                      ID.NIL,
                      2 // NSNotificationSuspensionBehaviorCoalesce
        );
      }
    };
  }

  protected enum Style {
    Legacy, Overlay;

    private static final Native<Style> CURRENT = new Native<>() {
      @Override
      public void run() {
        Style oldStyle = get();
        if (SystemInfoRt.isMac && !Registry.is("ide.mac.disableMacScrollbars", false)) {
          super.run();
        }
        Style newStyle = get();
        if (newStyle != oldStyle) {
          List<MacScrollBarUI> list = new ArrayList<>();
          processReferences(null, null, list);
          for (MacScrollBarUI ui : list) {
            ui.updateStyle(newStyle);
          }
        }
      }

      @NotNull
      @Override
      public Style produce() {
        ID style = invoke(getObjcClass("NSScroller"), "preferredScrollerStyle");
        Style value = 1 == style.intValue() ? Overlay : Legacy;
        Logger.getInstance(MacScrollBarUI.class).debug("scroll bar style ", value, " from ", style);
        return value;
      }

      @Override
      public String toString() {
        return "scroll bar style";
      }

      @Override
      ID initialize() {
        return invoke(invoke("NSNotificationCenter", "defaultCenter"),
                      "addObserver:selector:name:object:",
                      createDelegate("JBScrollBarStyleObserver", createSelector("handleScrollerStyleChanged:"), this),
                      createSelector("handleScrollerStyleChanged:"),
                      nsString("NSPreferredScrollerStyleDidChangeNotification"),
                      ID.NIL
        );
      }
    };
  }

  private static abstract class Native<T> implements Callback, Runnable, NotNullProducer<T> {
    private T myValue;

    Native() {
      Logger.getInstance(MacScrollBarUI.class).debug("initialize ", this);
      callMac(() -> initialize());
      EdtInvocationManager.invokeLaterIfNeeded(this);
    }

    abstract ID initialize();

    T get() {
      return myValue;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void callback(ID self, Pointer selector, ID event) {
      Logger.getInstance(MacScrollBarUI.class).debug("update ", this);
      EdtInvocationManager.invokeLaterIfNeeded(this);
    }

    @Override
    public void run() {
      myValue = callMac(this);
    }
  }
}
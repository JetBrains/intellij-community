/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.util.ui;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.mac.foundation.ID;
import com.sun.jna.Callback;
import com.sun.jna.Pointer;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.EventListenerList;
import java.util.EventListener;

import static com.intellij.ui.mac.foundation.Foundation.invoke;

class NSScrollerHelper {
  private static final Callback CALLBACK = new Callback() {
    @SuppressWarnings("UnusedDeclaration")
    public void callback(ID self, Pointer selector, ID event) {
      final Style style = getScrollerStyle();
      UIUtil.invokeLaterIfNeeded(new Runnable() {
        @Override
        public void run() {
          fireStyleChanged(style);
        }
      });
    }
  };

  public enum Style {Legacy, Overlay}

  private static final EventListenerList ourListeners = new EventListenerList();

  static {
    if (SystemInfo.isMac) {
      initNotificationObserver();
    }
  }

  private static void initNotificationObserver() {
    Foundation.NSAutoreleasePool pool = new Foundation.NSAutoreleasePool();

    ID delegateClass = Foundation.allocateObjcClassPair(Foundation.getObjcClass("NSObject"), "NSScrollerChangesObserver");
    if (!Foundation.addMethod(delegateClass, Foundation.createSelector("handleScrollerStyleChanged:"), CALLBACK, "v@")) {
      throw new RuntimeException("Cannot add observer method");
    }
    ;
    Foundation.registerObjcClassPair(delegateClass);
    ID delegate = invoke("NSScrollerChangesObserver", "new");

    try {
      ID defaultCenter = invoke("NSNotificationCenter", "defaultCenter");
      invoke(defaultCenter, "addObserver:selector:name:object:",
             delegate,
             Foundation.createSelector("handleScrollerStyleChanged:"),
             Foundation.nsString("NSPreferredScrollerStyleDidChangeNotification"),
             ID.NIL);
    }
    finally {
      pool.drain();
    }
  }

  @NotNull
  public static Style getScrollerStyle() {
    if (!SystemInfo.isMac) return Style.Legacy;

    Foundation.NSAutoreleasePool pool = new Foundation.NSAutoreleasePool();
    try {
      if (Foundation.invoke(Foundation.getObjcClass("NSScroller"), "preferredScrollerStyle").intValue() == 1) {
        return Style.Overlay;
      }
    }
    catch (Throwable ignore) {
    }
    finally {
      pool.drain();
    }
    return Style.Legacy;
  }

  public static void addScrollbarStyleListener(@NotNull ScrollbarStyleListener listener) {
    ourListeners.add(ScrollbarStyleListener.class, listener);
  }

  public static void removeScrollbarStyleListener(@NotNull ScrollbarStyleListener listener) {
    ourListeners.remove(ScrollbarStyleListener.class, listener);
  }

  private static void fireStyleChanged(@NotNull Style style) {
    Object[] listeners = ourListeners.getListenerList();
    for (int i = listeners.length - 2; i >= 0; i -= 2) {
      if (listeners[i] == ScrollbarStyleListener.class) {
        ((ScrollbarStyleListener)listeners[i + 1]).styleChanged(style);
      }
    }
  }

  public interface ScrollbarStyleListener extends EventListener {
    void styleChanged(@NotNull Style newStyle);
  }
}

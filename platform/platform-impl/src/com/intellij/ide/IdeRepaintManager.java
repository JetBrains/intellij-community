// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ide;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.reference.SoftReference;
import com.intellij.util.ReflectionUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.image.VolatileImage;
import java.lang.ref.WeakReference;
import java.util.Map;

/**
 * see http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6209673
 */
public class IdeRepaintManager extends RepaintManager {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.HackyRepaintManager");

  private Map<GraphicsConfiguration, VolatileImage> myImagesMap;

  WeakReference<JComponent> myLastComponent;

  @Override
  public Image getVolatileOffscreenBuffer(Component c, int proposedWidth, int proposedHeight) {
    final Image buffer = super.getVolatileOffscreenBuffer(c, proposedWidth, proposedHeight);
    clearLeakyImages(false); // DisplayChangedListener might be unavailable
    return buffer;
  }

  // sync here is to avoid data race when two(!) AWT threads on startup try to compete for the single myImagesMap
  private synchronized void clearLeakyImages(boolean force) {
    if (myImagesMap == null) {
      myImagesMap = ReflectionUtil.getField(RepaintManager.class, this, Map.class, "volatileMap");
    }

    if (force ||
        myImagesMap.size() > 3 /*leave no more than 3 images (usually one per screen) if DisplayChangedListener is not available */
       ) {
      //Force the RepaintManager to clear out all of the VolatileImage back-buffers that it has cached.
      //	See Sun bug 6209673.
      Dimension size = getDoubleBufferMaximumSize();
      setDoubleBufferMaximumSize(new Dimension(0, 0));
      setDoubleBufferMaximumSize(size);
    }
  }

  private class DisplayChangeHandler implements DisplayChangeDetector.Listener, Runnable {
    @Override
    public void displayChanged() {
      EventQueue.invokeLater(this);
    }
    @Override
    public void run() {
      clearLeakyImages(true);
    }
  }

  {
    DisplayChangeDetector.getInstance().addListener(new DisplayChangeHandler());
  }

  @Override
  public void addInvalidComponent(final JComponent invalidComponent) {
    checkThreadViolations(invalidComponent);

    super.addInvalidComponent(invalidComponent);
  }

  @Override
  public void addDirtyRegion(final JComponent c, final int x, final int y, final int w, final int h) {
    checkThreadViolations(c);

    super.addDirtyRegion(c, x, y, w, h);
  }

  private void checkThreadViolations(JComponent c) {
    if (!SwingUtilities.isEventDispatchThread() && c.isShowing()) {
      boolean repaint = false;
      boolean fromSwing = false;
      boolean swingKnownNonAwtOperations = false;
      final Exception exception = new Exception();
      StackTraceElement[] stackTrace = exception.getStackTrace();
      for (StackTraceElement st : stackTrace) {
        String className = st.getClassName();
        String methodName = st.getMethodName();

        if (repaint && className.startsWith("javax.swing.")) {
          fromSwing = true;
        }
        if (repaint && "imageUpdate".equals(methodName)) {
          swingKnownNonAwtOperations = true;
        }

        if ("read".equals(methodName) && className.startsWith("javax.swing.JEditorPane") ||
            "setCharacterAttributes".equals(methodName) && className.startsWith("javax.swing.text.DefaultStyledDocument")) {
          swingKnownNonAwtOperations = true;
          break;
        }

        if ("repaint".equals(methodName)) {
          repaint = true;
          fromSwing = false;
        }
      }
      if (swingKnownNonAwtOperations) {
        return;
      }
      if (repaint && !fromSwing) {
        //no problems here, since repaint() is thread safe
        return;
      }
      //ignore the last processed component
      if (SoftReference.dereference(myLastComponent) == c) {
        return;
      }
      myLastComponent = new WeakReference<>(c);

      LOG.warn("Access to realized (ever shown) UI components should be done only from the AWT event dispatch thread," +
               " revalidate(), invalidate() & repaint() is ok from any thread", exception);
    }
  }
}
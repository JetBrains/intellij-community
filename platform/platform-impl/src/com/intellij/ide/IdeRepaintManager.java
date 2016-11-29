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

/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Oct 30, 2006
 * Time: 8:41:56 PM
 */
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
  public void validateInvalidComponents() {
    super.validateInvalidComponents();
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
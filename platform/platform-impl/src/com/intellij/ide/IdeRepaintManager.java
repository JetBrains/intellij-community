/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.awt.image.VolatileImage;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.Map;

/**
 * see http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6209673
 */
public class IdeRepaintManager extends RepaintManager {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.HackyRepaintManager");

  private Map<GraphicsConfiguration, VolatileImage> myImagesMap;
  @NonNls private static final String FAULTY_FIELD_NAME = "volatileMap";

  WeakReference<JComponent> myLastComponent;

  public Image getVolatileOffscreenBuffer(Component c, int proposedWidth, int proposedHeight) {
    final Image buffer = super.getVolatileOffscreenBuffer(c, proposedWidth, proposedHeight);
    clearLeakyImages();
    return buffer;
  }

  @SuppressWarnings({"unchecked"})
  private void clearLeakyImages() {
    if (myImagesMap == null) {
      try {
        Field volMapField = RepaintManager.class.getDeclaredField(FAULTY_FIELD_NAME);
        volMapField.setAccessible(true);
        myImagesMap = (Map<GraphicsConfiguration, VolatileImage>)volMapField.get(this);
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }

    if (myImagesMap.size() > 3) {
      for (VolatileImage image : myImagesMap.values()) {
        image.flush();
      }
      myImagesMap.clear();
    }
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
        if (repaint && st.getClassName().startsWith("javax.swing.")) {
          fromSwing = true;
        }
        if (repaint && "imageUpdate".equals(st.getMethodName())) {
          swingKnownNonAwtOperations = true;
        }

        if (st.getClassName().startsWith("javax.swing.JEditorPane") && st.getMethodName().equals("read")) {
          swingKnownNonAwtOperations = true;
          break;
        }

        if ("repaint".equals(st.getMethodName())) {
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
      if (myLastComponent != null && c == myLastComponent.get()) {
        return;
      }
      myLastComponent = new WeakReference<JComponent>(c);

      LOG.warn("Access to realized (ever shown) UI components should be done only from the AWT event dispatch thread," +
               " revalidate(), invalidate() & repaint() is ok from any thread", exception);
    }
  }
}
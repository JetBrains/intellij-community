/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Oct 30, 2006
 * Time: 8:41:56 PM
 */
package com.intellij.ide;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.awt.image.VolatileImage;
import java.lang.reflect.Field;
import java.util.Map;

/**
 * see http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6209673
 */
public class HackyRepaintManager extends RepaintManager {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.HackyRepaintManager");

  private Map<GraphicsConfiguration, VolatileImage> myImagesMap;
  @NonNls private static final String FAULTY_FIELD_NAME = "volatileMap";

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
    final ApplicationEx app = (ApplicationEx)ApplicationManager.getApplication();
    if (app == null || app.isHeadlessEnvironment() || !app.isInternal()) return;

    if (!SwingUtilities.isEventDispatchThread() && c.isShowing()) {
      Exception exception = new Exception();
      boolean repaint = false;
      boolean fromSwing = false;
      StackTraceElement[] stackTrace = exception.getStackTrace();
      for (StackTraceElement st : stackTrace) {
        if (repaint && st.getClassName().startsWith("javax.swing.")) {
          fromSwing = true;
        }
        if ("repaint".equals(st.getMethodName())) {
          repaint = true;
        }
      }
      if (repaint && !fromSwing) {
        //no problems here, since repaint() is thread safe
        return;
      }
      LOG.warn("Access to realized UI components should be done only from AWT event dispatch thread, revalidate() and repaint() is ok from any thread", exception);
    }
  }
}
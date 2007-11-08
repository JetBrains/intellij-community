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
}
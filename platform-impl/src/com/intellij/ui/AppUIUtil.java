package com.intellij.ui;

import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.util.ImageLoader;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Method;
import java.util.List;
import java.util.ArrayList;

/**
 * @author yole
 */
public class AppUIUtil {
  private AppUIUtil() {
  }

  public static void updateFrameIcon(final Frame frame) {
    try {
      // new API added in JDK 1.6
      final Method method = frame.getClass().getMethod("setIconImages", List.class);
      method.invoke(frame, getAppIconImages());
    }
    catch (Exception e) {
      // fallback to JDK 1.5 API which doesn't support transparent PNGs as frame icons
      final Image image = ImageLoader.loadFromResource(ApplicationInfoImpl.getShadowInstance().getOpaqueIconUrl());
      frame.setIconImage(image);
    }
  }

  public static List<Image> getAppIconImages() {
    List<Image> images = new ArrayList<Image>();
    images.add(ImageLoader.loadFromResource(ApplicationInfoImpl.getShadowInstance().getIconUrl()));
    images.add(ImageLoader.loadFromResource(ApplicationInfoImpl.getShadowInstance().getSmallIconUrl()));
    return images;
  }

  public static void updateDialogIcon(final JDialog dialog) {
    UIUtil.updateDialogIcon(dialog, getAppIconImages());
  }
}

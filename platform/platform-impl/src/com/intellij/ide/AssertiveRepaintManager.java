// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.reference.SoftReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.applet.Applet;
import java.awt.*;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.List;

public final class AssertiveRepaintManager extends RepaintManager {
  private final static Logger LOG = Logger.getInstance(AssertiveRepaintManager.class);

  private WeakReference<Component> myLastComponent;


  public AssertiveRepaintManager() {
    super();

    // The only public constructor disables double buffering so we should try to enable it.
    try {
      Field defaultSettingField = RepaintManager.class.getDeclaredField("BUFFER_STRATEGY_TYPE");
      defaultSettingField.setAccessible(true);
      short defaultSetting = (short)defaultSettingField.get(null);

      Field bufferStrategyTypeField = RepaintManager.class.getDeclaredField("bufferStrategyType");
      bufferStrategyTypeField.setAccessible(true);
      bufferStrategyTypeField.set(this, defaultSetting);
    }
    catch (Throwable e) {
      LOG.warn("Could not set bufferingStrategy for AssertiveRepaintManager", e);
    }
  }

  @Override
  public synchronized void addInvalidComponent(JComponent invalidComponent) {
    checkThreadViolations(invalidComponent);
    super.addInvalidComponent(invalidComponent);
  }

  @Override
  public synchronized void removeInvalidComponent(JComponent component) {
    checkThreadViolations(component);
    super.removeInvalidComponent(component);
  }

  @Override
  public void addDirtyRegion(JComponent c, int x, int y, int w, int h) {
    checkThreadViolations(c);
    super.addDirtyRegion(c, x, y, w, h);
  }

  @Override
  public void addDirtyRegion(Window window, int x, int y, int w, int h) {
    checkThreadViolations(window);
    super.addDirtyRegion(window, x, y, w, h);
  }

  @Override
  public void addDirtyRegion(Applet applet, int x, int y, int w, int h) {
    checkThreadViolations(applet);
    super.addDirtyRegion(applet, x, y, w, h);
  }

  @Override
  public Rectangle getDirtyRegion(JComponent aComponent) {
    checkThreadViolations(aComponent);
    return super.getDirtyRegion(aComponent);
  }

  @Override
  public void markCompletelyDirty(JComponent aComponent) {
    checkThreadViolations(aComponent);
    super.markCompletelyDirty(aComponent);
  }

  @Override
  public void markCompletelyClean(JComponent aComponent) {
    checkThreadViolations(aComponent);
    super.markCompletelyClean(aComponent);
  }

  @Override
  public boolean isCompletelyDirty(JComponent aComponent) {
    checkThreadViolations(aComponent);
    return super.isCompletelyDirty(aComponent);
  }

  @Override
  public void validateInvalidComponents() {
    checkThreadViolations(null);
    super.validateInvalidComponents();
  }

  @Override
  public void paintDirtyRegions() {
    checkThreadViolations(null);
    super.paintDirtyRegions();
  }

  @Override
  public Image getOffscreenBuffer(Component c, int proposedWidth, int proposedHeight) {
    checkThreadViolations(c);
    return super.getOffscreenBuffer(c, proposedWidth, proposedHeight);
  }

  @Override
  public Image getVolatileOffscreenBuffer(Component c, int proposedWidth, int proposedHeight) {
    checkThreadViolations(c);
    return super.getVolatileOffscreenBuffer(c, proposedWidth, proposedHeight);
  }

  private void checkThreadViolations(@Nullable Component c) {
    if (!SwingUtilities.isEventDispatchThread() && (c == null || c.isShowing())) {
      final Exception exception = new Exception();
      StackTraceElement[] stackTrace = exception.getStackTrace();

      if (isAllowedViolation(c, stackTrace)) return;

      String categoriesString = Registry.stringValue("non.edt.swing.report.categories");
      List<String> categories = StringUtil.split(categoriesString, ",");
      if (shouldIgnoreStacktrace(categories, stackTrace)) return;

      LOG.warn("Access to realized (ever shown) UI components should be done only from the AWT event dispatch thread," +
               " revalidate(), invalidate() & repaint() is ok from any thread", exception);
    }
  }

  private static boolean shouldIgnoreStacktrace(@NotNull List<String> categories, StackTraceElement[] stackTrace) {
    if (categories.contains("none")) {
      return true;
    }

    boolean all = categories.contains("all");
    boolean categoryMatched = false;

    for (StackTraceElement element : stackTrace) {
      if (element.getClassName().equals("com.intellij.openapi.application.Preloader")) {
        if (all || categories.contains("preloader")) {
          categoryMatched = true;
          break;
        }
        else {
          return true;
        }
      }
      else if (element.getClassName().equals("com.intellij.serviceContainer.MyComponentAdapter")
               && element.getMethodName().equals("doCreateInstance")) {
        if (all || categories.contains("component")) {
          categoryMatched = true;
          break;
        }
        else {
          return true;
        }
      }
      else if (element.getClassName().equals("com.intellij.ui.CardLayoutPanel") && element.getMethodName().contains("selectLater")) {
        if (all || categories.contains("configurable")) {
          categoryMatched = true;
          break;
        }
        else {
          return true;
        }
      }
    }

    if (!all && !categoryMatched && !categories.contains("unknown")) {
      return true;
    }
    return false;
  }

  private boolean isAllowedViolation(@Nullable Component c, StackTraceElement[] stackTrace) {
    boolean repaint = false;
    boolean fromSwing = false;
    boolean swingKnownNonAwtOperations = false;
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
      return true;
    }
    if (repaint && !fromSwing) {
      //no problems here, since repaint() is thread safe
      return true;
    }
    //ignore the last processed component
    if (SoftReference.dereference(myLastComponent) == c) {
      return true;
    }
    myLastComponent = new WeakReference<>(c);
    return false;
  }
}

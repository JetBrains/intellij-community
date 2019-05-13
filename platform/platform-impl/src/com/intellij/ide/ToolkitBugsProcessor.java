// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.BitUtil;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

public class ToolkitBugsProcessor {
  private static final Logger LOG = Logger.getInstance("ToolkitBugProcessor");

  private final List<Handler> myHandlers = new ArrayList<>();

  public ToolkitBugsProcessor() {
    Class<?>[] classes = getClass().getDeclaredClasses();
    for (Class<?> each : classes) {
      if (!BitUtil.isSet(each.getModifiers(), Modifier.ABSTRACT) && Handler.class.isAssignableFrom(each)) {
        try {
          Handler eachHandler = (Handler)each.newInstance();
          if (eachHandler.isActual()) {
            myHandlers.add(eachHandler);
          }
        }
        catch (Throwable e) {
          LOG.error(e);
        }
      }
    }
  }

  public boolean process(Throwable e) {
    if (!Registry.is("ide.consumeKnownToolkitBugs")) return false;

    StackTraceElement[] stack = e.getStackTrace();
    for (Handler each : myHandlers) {
      if (each.process(e, stack)) {
        LOG.info("Ignored exception by toolkit bug processor, bug id=" + each.toString() + " desc=" + each.getDetails());
        return true;
      }
    }
    return false;
  }


  private abstract static class Handler {
    private final String myDetails;

    protected Handler() {
      myDetails = StringUtil.getShortName(getClass().getName());
    }

    protected Handler(String details) {
      myDetails = details;
    }

    public abstract boolean process(Throwable e, StackTraceElement[] stack);

    public boolean isActual() {
      return true;
    }

    public String getDetails() {
      return myDetails;
    }

    @Override
    public String toString() {
      String className = getClass().getName();
      String name = className.substring(className.lastIndexOf("$") + 1);
      if (name.startsWith("Sun_")) {
        name = name.substring("Sun_".length());
        return "http://bugs.sun.com/view_bug.do?bug_id=" + name;
      }
      return super.toString();
    }
  }

  @SuppressWarnings("UnusedDeclaration")
  private static class Sun_6857057 extends Handler {
    Sun_6857057() {
      super("text editor component - sync between model and view while dnd operations");
    }

    @Override
    public boolean process(Throwable e, StackTraceElement[] stack) {
      if (e instanceof ArrayIndexOutOfBoundsException && stack.length > 5) {
        return stack[0].getClassName().equals("sun.font.FontDesignMetrics")
            && stack[0].getMethodName().equals("charsWidth")
            && stack[5].getClassName().equals("javax.swing.text.CompositeView")
            && stack[5].getMethodName().equals("viewToModel");
      }
      return false;
    }
  }

  @SuppressWarnings("UnusedDeclaration")
  private static class Sun_6785663 extends Handler {
    Sun_6785663() {
      super("Nimbus L&F problem -- update style");
    }

    @Override
    public boolean process(Throwable e, StackTraceElement[] stack) {
      if (e instanceof ClassCastException && stack.length > 1) {
        return stack[0].getClassName().equals("javax.swing.plaf.synth.SynthButtonUI")
          && stack[0].getMethodName().equals("updateStyle");
      }
      return false;
    }
  }

  @SuppressWarnings("UnusedDeclaration")
  private static class Apple_ExceptionOnChangingMonitors extends Handler {
    Apple_ExceptionOnChangingMonitors() { }

    @Override
    public boolean isActual() {
      return SystemInfo.isMac;
    }

    @Override
    public boolean process(Throwable e, StackTraceElement[] stack) {
      if (e instanceof ArrayIndexOutOfBoundsException && stack.length > 1) {
        return stack[0].getClassName().equals("apple.awt.CWindow")
          && stack[0].getMethodName().equals("displayChanged");
      }

      return false;
    }

    @Override
    public String toString() {
      return "http://www.jetbrains.net/devnet/thread/278896";
    }
  }

  @SuppressWarnings("UnusedDeclaration")
  private static class Apple_CAccessible_NPE extends Handler {
    Apple_CAccessible_NPE() {
      super("apple.awt.CAccessible.getAccessibleContext(CAccessible.java:74)");
    }

    @Override
    public boolean isActual() {
      return SystemInfo.isMac;
    }

    @Override
    public boolean process(Throwable e, StackTraceElement[] stack) {
      if (e instanceof NullPointerException && stack.length > 1) {
        return SwingCleanuper.isCAccessible(stack[0].getClassName()) && stack[0].getMethodName().equals("getAccessibleContext");
      }
      return false;
    }
  }

  @SuppressWarnings("UnusedDeclaration")
  private static class HeadlessGraphicsEnvironmentUnderWindows extends Handler {
    HeadlessGraphicsEnvironmentUnderWindows() {
      super("HeadlessGraphicsEnvironment cannot be cast to Win32GraphicsEnvironment");
    }

    @Override
    public boolean process(Throwable e, StackTraceElement[] stack) {
      // http://bugs.java.com/bugdatabase/view_bug.do?bug_id=6607186
      if (e instanceof ClassCastException && stack.length > 1 && e.getMessage() != null) {
        return e.getMessage().equals("sun.java2d.HeadlessGraphicsEnvironment cannot be cast to sun.awt.Win32GraphicsEnvironment");
      }
      return false;
    }
  }
}

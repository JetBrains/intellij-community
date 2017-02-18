/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
    public Sun_6857057() {
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
    public Sun_6785663() {
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
  private static class Tricky_JEditorPane_registerEditorKitForContentType_NPE extends Handler {
    public Tricky_JEditorPane_registerEditorKitForContentType_NPE() {
      super("http://ea.jetbrains.com/browser/ea_problems/13587 - JEditorPane_registerEditorKitForContentType_NPE");
    }

    @Override
    public boolean process(Throwable e, StackTraceElement[] stack) {
      if (e instanceof NullPointerException && stack.length > 3) {
        //bombed for possible future fix
        if (SystemInfo.isJavaVersionAtLeast("1.7")) return false;
        
        return stack[0].getClassName().equals("java.util.Hashtable")
          && stack[0].getMethodName().equals("put")
          && stack[3].getClassName().equals("javax.swing.JEditorPane")
          && stack[3].getMethodName().equals("loadDefaultKitsIfNecessary");
      }
      return false;
    }
  }

  @SuppressWarnings("UnusedDeclaration")
  private static class Apple_ExceptionOnChangingMonitors extends Handler {
    public Apple_ExceptionOnChangingMonitors() { }

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
    public Apple_CAccessible_NPE() {
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
}

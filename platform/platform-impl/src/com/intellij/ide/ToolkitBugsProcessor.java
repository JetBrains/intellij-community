/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

public class ToolkitBugsProcessor {

  private static final Logger LOG = Logger.getInstance("ToolkitBugProcessor");

  List<Handler> myHandlers = new ArrayList<Handler>();

  public ToolkitBugsProcessor() {
    Class<?>[] classes = getClass().getDeclaredClasses();
    for (Class<?> each : classes) {
      if ((each.getModifiers() & Modifier.ABSTRACT) > 0) continue;
      if (Handler.class.isAssignableFrom(each)) {
        try {
          Handler eachHandler = (Handler)each.newInstance();
          if (eachHandler.isActual()) {
            myHandlers.add(eachHandler);
          }
        }
        catch (Throwable e) {
          LOG.error(e);
          continue;
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


  abstract static class Handler {

    private String myDetails;

    protected Handler() {
      myDetails = StringUtil.getShortName(getClass().getName());
    }

    protected Handler(String details) {
      myDetails = details;
    }

    abstract boolean process(Throwable e, StackTraceElement[] stack);

    boolean isActual() {
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

  static class Sun_6857057 extends Handler {
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

  static class Sun_6785663 extends Handler {
    Sun_6785663() {
      super("Numbus L&F problem -- update style");
    }

    @Override
    boolean process(Throwable e, StackTraceElement[] stack) {
      if (e instanceof ClassCastException && stack.length > 1) {
        return stack[0].getClassName().equals("javax.swing.plaf.synth.SynthButtonUI")
          && stack[0].getMethodName().equals("updateStyle");
      }
      return false;
    }
  }

  static class Sun_6322854 extends Handler {
    Sun_6322854() {
      super("NPE - Failed to retrieve atom name");
    }

    @Override
    boolean process(Throwable e, StackTraceElement[] stack) {
      if (e instanceof NullPointerException && stack.length > 2) {
        return (e.getMessage() != null && e.getMessage().startsWith("Failed to retrieve atom name"))
          && stack[1].getClassName().equals("sun.awt.X11.XAtom");

      }
      return false;
    }
  }

  static class Apple_ExceptionOnChangingMonitors extends Handler {

    @Override
    boolean isActual() {
      return SystemInfo.isMac;
    }

    @Override
    boolean process(Throwable e, StackTraceElement[] stack) {
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
}

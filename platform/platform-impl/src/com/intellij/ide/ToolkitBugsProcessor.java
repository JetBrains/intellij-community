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
import com.intellij.openapi.util.registry.Registry;

import java.util.ArrayList;
import java.util.List;

public class ToolkitBugsProcessor {

  private static final Logger LOG = Logger.getInstance("ToolkirBugProcessor");

  List<Handler> myHandlers = new ArrayList<Handler>();

  public ToolkitBugsProcessor() {
    Class<?>[] classes = getClass().getDeclaredClasses();
    for (Class<?> each : classes) {
      if (each.equals(Handler.class)) continue;
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

    for (Handler each : myHandlers) {
      if (each.process(e)) {
        LOG.info("Ignored exception by toolkit bug processor, bug id=" + each.toString());
        return true;
      }
    }
    return false;
  }


  abstract static class Handler {

    abstract boolean process(Throwable e);

    boolean isActual() {
      return true;
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
    @Override
    public boolean process(Throwable e) {
      if (e instanceof ArrayIndexOutOfBoundsException) {
        StackTraceElement[] stack = e.getStackTrace();
        if (stack.length > 5) {
          if (stack[0].getClassName().equals("sun.font.FontDesignMetrics")
              && stack[0].getMethodName().equals("charsWidth")
              && stack[5].getClassName().equals("javax.swing.text.CompositeView")
              && stack[5].getMethodName().equals("viewToModel")) {
            return true;
          }
        }
      }
      return false;
    }
  }
}

/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.debugger.memory.utils;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.openapi.util.text.StringUtil;
import com.sun.jdi.Location;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class StackFrameItem {
  private final String myFilePath;
  private final String myMethodName;
  private final int myLineNumber;

  public StackFrameItem(@NotNull String path, @NotNull String methodName, int line) {
    myFilePath = path.replace('\\', '.');
    myMethodName = methodName;
    myLineNumber = line;
  }

  @NotNull
  public String path() {
    return myFilePath;
  }

  @NotNull
  public String methodName() {
    return myMethodName;
  }

  @NotNull
  public String className() {
    return StringUtil.getShortName(myFilePath);
  }

  @NotNull
  public String packageName() {
    return StringUtil.getPackageName(myFilePath);
  }

  public int line() {
    return myLineNumber;
  }

  public static List<StackFrameItem> createFrames(ThreadReferenceProxyImpl threadReferenceProxy) throws EvaluateException {
    List<StackFrameProxyImpl> stack = threadReferenceProxy == null ? null : threadReferenceProxy.frames();

    if (stack != null) {
      return StreamEx.of(stack).map(frame -> {
        try {
          Location loc = frame.location();
          return new StackFrameItem(loc.declaringType().name(), loc.method().name(), loc.lineNumber());
        }
        catch (EvaluateException e) {
          return null;
        }
      }).nonNull().toList();
    }
    return Collections.emptyList();
  }
}

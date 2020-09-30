// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine;

import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.frame.XValueNode;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * Represents an object that refers to some object
 */
public interface ReferringObject {
  @NotNull
  ValueDescriptorImpl createValueDescription(@NotNull Project project, @NotNull Value referee);

  @Nullable
  String getNodeName(int order);

  @Nullable
  ObjectReference getReference();

  @NotNull
  Function<XValueNode, XValueNode> getNodeCustomizer();
}

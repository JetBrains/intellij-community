// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.CommonProblemDescriptor;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

public interface InspectionProblemConsumer {
  void consume(@NotNull Element element, @NotNull CommonProblemDescriptor descriptor, @NotNull InspectionToolWrapper<?, ?> toolWrapper);
}

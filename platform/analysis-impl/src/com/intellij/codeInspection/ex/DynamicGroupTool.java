// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.LocalInspectionTool;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * An {@link LocalInspectionTool} inheritor implementing this interface can provide child inspections generated dynamically at runtime.
 * These can then be separately enabled, disabled and configured by the user.
 *
 * @see com.intellij.codeInspection.LocalInspectionEP#dynamicGroup
 * @see com.intellij.codeInsight.daemon.impl.ProblemDescriptorWithReporterName if the parent inspection does all the work
 * and should report the problems in the name of the child inspections.
 */
public interface DynamicGroupTool {
  @NotNull
  List<LocalInspectionToolWrapper> getChildren();
}

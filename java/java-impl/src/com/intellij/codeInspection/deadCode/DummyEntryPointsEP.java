// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.deadCode;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.codeInspection.InspectionEP;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.ide.plugins.cl.PluginAwareClassLoader;
import com.intellij.openapi.extensions.DefaultPluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import org.jetbrains.annotations.NotNull;

public final class DummyEntryPointsEP extends InspectionEP {
  public DummyEntryPointsEP() {
    presentation = DummyEntryPointsPresentation.class.getName();
    displayName = AnalysisBundle.message("inspection.dead.code.entry.points.display.name");
    implementationClass = "";
    shortName = "";
    ClassLoader classLoader = DummyEntryPointsEP.class.getClassLoader();
    setPluginDescriptor(classLoader instanceof PluginAwareClassLoader
                        ? ((PluginAwareClassLoader)classLoader).getPluginDescriptor()
                        : new DefaultPluginDescriptor(PluginId.getId("DummyEntryPointsEP"), classLoader));
  }

  @Override
  public @NotNull InspectionProfileEntry instantiateTool() {
    return new DummyEntryPointsTool();
  }
}

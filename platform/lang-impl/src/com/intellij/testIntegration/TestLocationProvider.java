package com.intellij.testIntegration;

import com.intellij.execution.Location;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Roman Chernyatchik
 */
public interface TestLocationProvider {
  ExtensionPointName<TestLocationProvider> EP_NAME = ExtensionPointName.create("com.intellij.testSrcLocator");

  @NotNull
  List<Location> getLocation(@NotNull final String protocolId,
                             @NotNull final String locationData,
                             final Project project);
}

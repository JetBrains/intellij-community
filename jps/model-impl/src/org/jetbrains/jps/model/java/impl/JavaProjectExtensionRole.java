// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.java.impl;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementCreator;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.JpsJavaProjectExtension;

/**
 * This class is for internal use only, call {@link JpsJavaExtensionService#getProjectExtension(JpsProject)} to get an instance of 
 * {@link JpsJavaProjectExtension} for a project. 
 */
@ApiStatus.Internal
public class JavaProjectExtensionRole extends JpsElementChildRoleBase<JpsJavaProjectExtension> implements JpsElementCreator<JpsJavaProjectExtension> {
  public static final JavaProjectExtensionRole INSTANCE = new JavaProjectExtensionRole();

  public JavaProjectExtensionRole() {
    super("java project extension");
  }

  @Override
  public @NotNull JpsJavaProjectExtension create() {
    return new JpsJavaProjectExtensionImpl();
  }
}

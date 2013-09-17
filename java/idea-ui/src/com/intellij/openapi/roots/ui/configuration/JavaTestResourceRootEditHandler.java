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
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.icons.AllIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaResourceRootType;

import javax.swing.*;
import java.awt.*;

/**
 * @author nik
 */
public class JavaTestResourceRootEditHandler extends JavaResourceRootEditHandlerBase {
  public JavaTestResourceRootEditHandler() {
    super(JavaResourceRootType.TEST_RESOURCE);
  }

  @NotNull
  @Override
  public String getRootTypeName() {
    return "Test Resources";
  }

  @NotNull
  @Override
  public Icon getRootIcon() {
    return AllIcons.Modules.TestResourcesRoot;
  }

  @NotNull
  @Override
  public String getRootsGroupTitle() {
    return "Test Resource Folders";
  }

  @NotNull
  @Override
  public Color getRootsGroupColor() {
    return new Color(0x739503);
  }

  @NotNull
  @Override
  public String getUnmarkRootButtonText() {
    return "Unmark Test Resource";
  }
}

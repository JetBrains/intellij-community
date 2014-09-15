/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaResourceRootType;

import javax.swing.*;
import java.awt.*;

/**
 * @author nik
 */
public class JavaResourceRootEditHandler extends JavaResourceRootEditHandlerBase {
  public JavaResourceRootEditHandler() {
    super(JavaResourceRootType.RESOURCE);
  }

  @NotNull
  @Override
  public String getRootTypeName() {
    return "Resources";
  }

  @NotNull
  @Override
  public Icon getRootIcon() {
    return AllIcons.Modules.ResourcesRoot;
  }

  @NotNull
  @Override
  public String getRootsGroupTitle() {
    return "Resource Folders";
  }

  @NotNull
  @Override
  public Color getRootsGroupColor() {
    return new JBColor(new Color(0x812DF3), new Color(127, 96, 144));
  }

  @NotNull
  @Override
  public String getUnmarkRootButtonText() {
    return "Unmark Resource";
  }
}

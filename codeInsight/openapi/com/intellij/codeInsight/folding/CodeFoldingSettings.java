/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package com.intellij.codeInsight.folding;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import org.jetbrains.annotations.NotNull;

public abstract class CodeFoldingSettings implements ApplicationComponent {

  public static CodeFoldingSettings getInstance() {
    return ApplicationManager.getApplication().getComponent(CodeFoldingSettings.class);
  }

  public abstract boolean isCollapseImports();
  public abstract void setCollapseImports(boolean value);

  public abstract boolean isCollapseMethods();
  public abstract void setCollapseMethods(boolean value);

  public abstract boolean isCollapseAccessors();
  public abstract void setCollapseAccessors(boolean value);

  public abstract boolean isCollapseInnerClasses();
  public abstract void setCollapseInnerClasses(boolean value);

  public abstract boolean isCollapseJavadocs();
  public abstract void setCollapseJavadocs(boolean value);

  public abstract boolean isCollapseFileHeader();
  public abstract void setCollapseFileHeader(boolean value);

  public abstract boolean isCollapseAnonymousClasses();
  public abstract void setCollapseAnonymousClasses(boolean value);

  public abstract boolean isCollapseAnnotations();
  public abstract void setCollapseAnnotations(boolean value);

  @NotNull
  public String getComponentName() {
    return "CodeFoldingSettings";
  }

  public void initComponent() { }

  public void disposeComponent() {
  }

}

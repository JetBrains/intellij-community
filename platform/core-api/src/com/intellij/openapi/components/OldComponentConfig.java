/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.components;

import com.intellij.util.xmlb.annotations.Tag;

/**
 * @author Dmitry Avdeev
 */
@Tag("component")
public class OldComponentConfig extends ComponentConfig {

  @Tag(value = "skipForDefaultProject", textIfEmpty="true")
  public boolean skipForDefaultProject;

  @Tag("headless-implementation-class")
  @Override
  public void setHeadlessImplementationClass(String headlessImplementationClass) {
    super.setHeadlessImplementationClass(
      headlessImplementationClass);
  }

  @Tag(value = "loadForDefaultProject", textIfEmpty="true")
  @Override
  public void setLoadForDefaultProject(boolean loadForDefaultProject) {
    super.setLoadForDefaultProject(loadForDefaultProject);
  }

  @Tag("interface-class")
  @Override
  public void setInterfaceClass(String interfaceClass) {
    super.setInterfaceClass(interfaceClass);
  }

  @Tag("implementation-class")
  @Override
  public void setImplementationClass(String implementationClass) {
    super.setImplementationClass(implementationClass);
  }

  @Override
  public boolean isLoadForDefaultProject() {
    return super.isLoadForDefaultProject() && !skipForDefaultProject;
  }
}

// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components;

import com.intellij.util.xmlb.annotations.Tag;

/**
 * @author Dmitry Avdeev
 */
@Tag("component")
public final class OldComponentConfig extends ComponentConfig {
  /**
   * @deprecated project components aren't loaded in the default project by default so there is not need to use this tag;
   * use {@link #setLoadForDefaultProject(boolean) 'loadForDefaultProject'} if your really need to have your component in the default project.
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  @Tag(value = "skipForDefaultProject", textIfEmpty="true")
  public boolean skipForDefaultProject;

  @Tag("headless-implementation-class")
  @Override
  public void setHeadlessImplementationClass(String headlessImplementationClass) {
    super.setHeadlessImplementationClass(headlessImplementationClass);
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

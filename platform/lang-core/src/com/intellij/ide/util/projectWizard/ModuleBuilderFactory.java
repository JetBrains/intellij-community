// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.projectWizard;

import com.intellij.util.xmlb.annotations.Attribute;

public final class ModuleBuilderFactory {
  @Attribute("builderClass")
  public String builderClass;
}

// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.semantic;

import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.util.xmlb.annotations.Attribute;

/**
 * @author peter
 */
public final class SemContributorEP extends AbstractExtensionPointBean {
  @Attribute("implementation")
  public String implementation;
}

// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.semantic;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;
import org.picocontainer.PicoContainer;

/**
 * @author peter
 */
public class SemContributorEP extends AbstractExtensionPointBean {
  private static final Logger LOG = Logger.getInstance(SemContributorEP.class);

  @Attribute("implementation")
  public String implementation;

  public void registerSemProviders(PicoContainer container, @NotNull SemRegistrar registrar) {
    try {
      final SemContributor contributor = instantiate(implementation, container);
      contributor.registerSemProviders(registrar);
    }
    catch (ClassNotFoundException e) {
      LOG.error(e);
    }
  }
}

package com.intellij.semantic;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.util.xmlb.annotations.Attribute;
import org.picocontainer.PicoContainer;

/**
 * @author peter
 */
public class SemContributorEP extends AbstractExtensionPointBean {
  private static final Logger LOG = Logger.getInstance("#com.intellij.semantic.SemContributorEP");

  @Attribute("implementation")
  public String implementation;

  public void registerSemProviders(PicoContainer container, SemRegistrar registrar) {
    try {
      final SemContributor contributor = instantiate(implementation, container);
      contributor.registerSemProviders(registrar);
    }
    catch (ClassNotFoundException e) {
      LOG.error(e);
    }
  }

}

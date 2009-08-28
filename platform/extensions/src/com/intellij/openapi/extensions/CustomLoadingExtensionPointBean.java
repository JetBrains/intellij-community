package com.intellij.openapi.extensions;

import com.intellij.util.xmlb.annotations.Attribute;
import org.picocontainer.PicoContainer;

/**
 * @author yole
 */
public class CustomLoadingExtensionPointBean extends AbstractExtensionPointBean {
  @Attribute("factoryClass")
  public String factoryClass;

  @Attribute("factoryArgument")
  public String factoryArgument;

  protected Object instantiateExtension(final String implementationClass, final PicoContainer picoContainer) throws ClassNotFoundException {
    if (factoryClass != null) {
      ExtensionFactory factory = instantiate(factoryClass, picoContainer);
      return factory.createInstance(factoryArgument, implementationClass);
    }
    else {
      if (implementationClass == null) {
        throw new RuntimeException("implementation class is not specified for unknown language extension point, " +
                                   "plugin id: " +
                                   (myPluginDescriptor == null ? "<not available>" : myPluginDescriptor.getPluginId()) + ". " +
                                   "Check if 'implementationClass' attribute is specified");
      }
      //noinspection unchecked
      return instantiate(implementationClass, picoContainer);
    }
  }
}

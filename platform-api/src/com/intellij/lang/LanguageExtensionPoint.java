package com.intellij.lang;

import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.KeyedLazyInstance;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class LanguageExtensionPoint<T> extends AbstractExtensionPointBean implements KeyedLazyInstance<T> {

  // these must be public for scrambling compatibility
  @Attribute("language")
  public String language;

  @Attribute("implementationClass")
  public String implementationClass;

  @Attribute("factoryClass")
  public String factoryClass;

  @Attribute("factoryArgument")
  public String factoryArgument;

  private final NotNullLazyValue<T> myHandler = new NotNullLazyValue<T>() {
    @NotNull
    protected T compute() {
      try {
        if (factoryClass != null) {
          ExtensionFactory factory = instantiate(factoryClass, ApplicationManager.getApplication().getPicoContainer());
          return (T)factory.createInstance(factoryArgument, implementationClass);
        }
        else {
          if (implementationClass == null) {
            throw new RuntimeException("implementation class is not specified for unknown language extension point, " +
                                       "language: " + language + ", plugin id: " +
                                       (myPluginDescriptor == null ? "<not available>" : myPluginDescriptor.getPluginId()) + ". " +
                                       "Check if 'implementationClass' attribute is specified");
          }
          //noinspection unchecked
          return (T)LanguageExtensionPoint.this.instantiate(implementationClass, ApplicationManager.getApplication().getPicoContainer());
        }
      }
      catch (ClassNotFoundException e) {
        throw new RuntimeException(e);
      }
    }
  };

  public T getInstance() {
    return myHandler.getValue();
  }

  public String getKey() {
    return language;
  }
}
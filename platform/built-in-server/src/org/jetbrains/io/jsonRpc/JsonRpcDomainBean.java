package org.jetbrains.io.jsonRpc;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.AtomicNotNullLazyValue;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;

public class JsonRpcDomainBean extends AbstractExtensionPointBean {
  public static final ExtensionPointName<JsonRpcDomainBean> EP_NAME = ExtensionPointName.create("org.jetbrains.jsonRpcDomain");

  @Attribute("name")
  public String name;

  @Attribute("implementation")
  public String implementation;

  @Attribute("service")
  public String service;

  @Attribute("asInstance")
  public boolean asInstance = true;

  @Attribute("overridable")
  public boolean overridable;

  private NotNullLazyValue<?> value;

  @NotNull
  public NotNullLazyValue<?> getValue() {
    if (value == null) {
      value = new AtomicNotNullLazyValue<Object>() {
        @NotNull
        @Override
        protected Object compute() {
          try {
            if (service == null) {
              Class<Object> aClass = findClass(implementation);
              return asInstance ? instantiate(aClass, ApplicationManager.getApplication().getPicoContainer(), true) : aClass;
            }
            else {
              return ServiceManager.getService(findClass(service));
            }
          }
          catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
          }
        }
      };
    }
    return value;
  }
}
/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.reflect;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.picocontainer.defaults.ConstructorInjectionComponentAdapter;

/**
 * @author peter
 */
public class DomExtenderEP {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.xml.reflect.DomExtenderEP");
  public static final ExtensionPointName<DomExtenderEP> EP_NAME = ExtensionPointName.create("com.intellij.dom.extender");

  @Attribute("domClass")
  public String domClassName;
  @Attribute("extenderClass")
  public String extenderClassName;

  private Class<?> myDomClass;
  private DomExtender myExtender;
  private PluginDescriptor myPluginDescriptor;

  public void setPluginDescriptor(PluginDescriptor pluginDescriptor) {
    myPluginDescriptor = pluginDescriptor;
  }

  @Nullable
  public DomExtensionsRegistrarImpl extend(@NotNull final Project project, @NotNull final DomElement element, @Nullable DomExtensionsRegistrarImpl registrar) {
    if (myExtender == null) {
      try {
        myDomClass = findClass(domClassName);
        myExtender = (DomExtender<?>) new ConstructorInjectionComponentAdapter(extenderClassName, findClass(extenderClassName)).getComponentInstance(project.getPicoContainer());
      }
      catch(Exception e) {
        LOG.error(e);
        return null;
      }
    }
    if (myDomClass.isInstance(element)) {
      if (registrar == null) {
        registrar = new DomExtensionsRegistrarImpl();
      }
      registrar.addDependencies(myExtender.registerExtensions(element, registrar));
    }
    return registrar;
  }

  private Class<?> findClass(final String className) throws ClassNotFoundException {
    return Class.forName(className, true,
                                          myPluginDescriptor == null ? getClass().getClassLoader()  : myPluginDescriptor.getPluginClassLoader());
  }


}

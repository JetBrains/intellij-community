/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.reflect;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class DomExtenderEP extends AbstractExtensionPointBean {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.xml.reflect.DomExtenderEP");
  public static final ExtensionPointName<DomExtenderEP> EP_NAME = ExtensionPointName.create("com.intellij.dom.extender");

  @Attribute("domClass")
  public String domClassName;
  @Attribute("extenderClass")
  public String extenderClassName;

  private Class<?> myDomClass;
  private DomExtender myExtender;


  @Nullable
  public DomExtensionsRegistrarImpl extend(@NotNull final Project project, @NotNull final DomElement element, @Nullable DomExtensionsRegistrarImpl registrar) {
    if (myExtender == null) {
      try {
        myDomClass = findClass(domClassName);
        myExtender = instantiate(extenderClassName, project.getPicoContainer());
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

}

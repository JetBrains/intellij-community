// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.lw;

import org.jdom.Element;

public class LwAtomicComponent extends LwComponent {
  LwAtomicComponent(final String className){
    super(className);
  }

  @Override
  public void read(final Element element, final PropertiesProvider provider) throws Exception{
    readBase(element);
    readConstraints(element);
    readProperties(element, provider);
  }
}

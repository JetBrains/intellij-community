// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xml.impl;

import com.intellij.ide.ApplicationInitializedListener;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiType;
import com.intellij.util.Consumer;
import com.intellij.util.xml.CanonicalPsiTypeConverterImpl;
import com.intellij.util.xml.ConverterManager;
import com.intellij.util.xml.PsiClassConverter;
import com.intellij.util.xml.ui.DomUIFactory;
import com.intellij.util.xml.ui.PsiClassControl;
import com.intellij.util.xml.ui.PsiClassTableCellEditor;
import com.intellij.util.xml.ui.PsiTypeControl;

/**
 * @author peter
 */
final class JavaDomApplicationComponent implements Consumer<DomUIFactory> {
  static final class Initializer implements ApplicationInitializedListener {
    @Override
    public void componentsInitialized() {
      ConverterManager converterManager = ServiceManager.getService(ConverterManager.class);
      converterManager.addConverter(PsiClass.class, new PsiClassConverter());
      converterManager.addConverter(PsiType.class, new CanonicalPsiTypeConverterImpl());
    }
  }

  @Override
  public void consume(DomUIFactory factory) {
    factory.registerCustomControl(PsiClass.class, wrapper -> new PsiClassControl(wrapper, false));
    factory.registerCustomControl(PsiType.class, wrapper -> new PsiTypeControl(wrapper, false));

    factory.registerCustomCellEditor(PsiClass.class,
                                     element -> new PsiClassTableCellEditor(element.getManager().getProject(), element.getResolveScope()));
  }
}
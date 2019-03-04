/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.xml.impl;

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
public class JavaDomApplicationComponent implements Consumer<DomUIFactory> {
  public JavaDomApplicationComponent(ConverterManager converterManager) {
    converterManager.addConverter(PsiClass.class, new PsiClassConverter());
    converterManager.addConverter(PsiType.class, new CanonicalPsiTypeConverterImpl());
  }

  @Override
  public void consume(DomUIFactory factory) {
    factory.registerCustomControl(PsiClass.class, wrapper -> new PsiClassControl(wrapper, false));
    factory.registerCustomControl(PsiType.class, wrapper -> new PsiTypeControl(wrapper, false));

    factory.registerCustomCellEditor(PsiClass.class,
                                     element -> new PsiClassTableCellEditor(element.getManager().getProject(), element.getResolveScope()));
  }
}
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
import com.intellij.util.Function;
import com.intellij.util.xml.*;
import com.intellij.util.xml.converters.values.ClassValueConverter;
import com.intellij.util.xml.converters.values.ClassArrayConverter;
import com.intellij.util.xml.ui.*;

import javax.swing.table.TableCellEditor;

/**
 * @author peter
 */
public class JavaDomApplicationComponent implements Consumer<DomUIFactory> {
  public JavaDomApplicationComponent(ConverterManager converterManager) {
    converterManager.addConverter(PsiClass.class, new PsiClassConverter());
    converterManager.addConverter(PsiType.class, new CanonicalPsiTypeConverterImpl());
    converterManager.registerConverterImplementation(JvmPsiTypeConverter.class, new JvmPsiTypeConverterImpl());
    converterManager.registerConverterImplementation(CanonicalPsiTypeConverter.class, new CanonicalPsiTypeConverterImpl());

    final ClassValueConverter classValueConverter = ClassValueConverter.getClassValueConverter();
    converterManager.registerConverterImplementation(ClassValueConverter.class, classValueConverter);
    final ClassArrayConverter classArrayConverter = ClassArrayConverter.getClassArrayConverter();
    converterManager.registerConverterImplementation(ClassArrayConverter.class, classArrayConverter);

  }

  @Override
  public void consume(DomUIFactory factory) {
    factory.registerCustomControl(PsiClass.class, new Function<DomWrapper<String>, BaseControl>() {
      public BaseControl fun(final DomWrapper<String> wrapper) {
        return new PsiClassControl(wrapper, false);
      }
    });
    factory.registerCustomControl(PsiType.class, new Function<DomWrapper<String>, BaseControl>() {
      public BaseControl fun(final DomWrapper<String> wrapper) {
        return new PsiTypeControl(wrapper, false);
      }
    });

    factory.registerCustomCellEditor(PsiClass.class, new Function<DomElement, TableCellEditor>() {
      public TableCellEditor fun(final DomElement element) {
        return new PsiClassTableCellEditor(element.getManager().getProject(), element.getResolveScope());
      }
    });
  }
}
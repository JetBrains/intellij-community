/*
 * Copyright 2000-2006 JetBrains s.r.o.
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

package com.intellij.uiDesigner.compiler;

import com.intellij.uiDesigner.lw.LwComponent;
import com.intellij.uiDesigner.lw.LwContainer;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.Type;

import java.awt.*;

/**
 * @author yole
 */
public class BorderLayoutCodeGenerator extends LayoutCodeGenerator {
  private static Type ourBorderLayoutType = Type.getType(BorderLayout.class);
  private static Method ourConstructor = Method.getMethod("void <init>(int,int)");

  public void generateContainerLayout(final LwComponent lwComponent, final GeneratorAdapter generator, final int componentLocal) {
    if (lwComponent instanceof LwContainer) {
      LwContainer container = (LwContainer) lwComponent;

      generator.loadLocal(componentLocal);

      BorderLayout borderLayout = (BorderLayout) container.getLayout();
      generator.newInstance(ourBorderLayoutType);
      generator.dup();
      generator.push(borderLayout.getHgap());
      generator.push(borderLayout.getVgap());
      generator.invokeConstructor(ourBorderLayoutType, ourConstructor);

      generator.invokeVirtual(ourContainerType, ourSetLayoutMethod);
    }
  }

  public void generateComponentLayout(final LwComponent lwComponent,
                                      final GeneratorAdapter generator,
                                      final int componentLocal,
                                      final int parentLocal) {
    generator.loadLocal(parentLocal);
    generator.loadLocal(componentLocal);
    generator.push((String) lwComponent.getCustomLayoutConstraints());
    generator.invokeVirtual(ourContainerType, ourAddMethod);
  }
}

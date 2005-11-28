/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.Opcodes;

import java.awt.*;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 17.11.2005
 * Time: 19:58:42
 * To change this template use File | Settings | File Templates.
 */
public abstract class LayoutCodeGenerator {
  public abstract void generateContainerLayout(final LwComponent lwComponent, final GeneratorAdapter generator, final int componentLocal);
  public abstract void generateComponentLayout(final LwComponent lwComponent, final GeneratorAdapter generator, final int componentLocal,
                                               final int parentLocal);

  protected void newDimensionOrNull(final GeneratorAdapter generator, final Dimension dimension) {
    if (dimension.width == -1 && dimension.height == -1) {
      generator.visitInsn(Opcodes.ACONST_NULL);
    }
    else {
      AsmCodeGenerator.pushPropValue(generator, "java.awt.Dimension", dimension);
    }
  }

  public String mapComponentClass(final String componentClassName) {
    return componentClassName;
  }
}

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
import com.intellij.uiDesigner.core.GridConstraints;
import com.jgoodies.forms.layout.FormLayout;
import com.jgoodies.forms.layout.CellConstraints;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

/**
 * @author yole
 */
public class FormLayoutCodeGenerator extends LayoutCodeGenerator {
  private static final Type ourFormLayoutType = Type.getType(FormLayout.class);
  private static final Type ourCellConstraintsType = Type.getType(CellConstraints.class);
  private static final Type ourCellAlignmentType = Type.getType(CellConstraints.Alignment.class);
  private static final Method ourFormLayoutConstructor = Method.getMethod("void <init>(java.lang.String,java.lang.String)");
  private static final Method ourCellConstraintsConstructor = Method.getMethod("void <init>(int,int,int,int,com.jgoodies.forms.layout.CellConstraints$Alignment,com.jgoodies.forms.layout.CellConstraints$Alignment)");

  public static String[] HORZ_ALIGN_FIELDS = new String[] { "LEFT", "CENTER", "RIGHT", "FILL" };
  public static String[] VERT_ALIGN_FIELDS = new String[] { "TOP", "CENTER", "BOTTOM", "FILL" };

  public void generateContainerLayout(final LwContainer lwContainer, final GeneratorAdapter generator, final int componentLocal) {
    FormLayout formLayout = (FormLayout) lwContainer.getLayout();

    generator.loadLocal(componentLocal);

    generator.newInstance(ourFormLayoutType);
    generator.dup();
    generator.push(Utils.getEncodedColumnSpecs(formLayout));
    generator.push(Utils.getEncodedRowSpecs(formLayout));

    generator.invokeConstructor(ourFormLayoutType, ourFormLayoutConstructor);

    generator.invokeVirtual(ourContainerType, ourSetLayoutMethod);
  }

  public void generateComponentLayout(final LwComponent lwComponent, final GeneratorAdapter generator, final int componentLocal, final int parentLocal) {
    generator.loadLocal(parentLocal);
    generator.loadLocal(componentLocal);
    addNewCellConstraints(generator, lwComponent);
    generator.invokeVirtual(ourContainerType, ourAddMethod);
  }

  private static void addNewCellConstraints(final GeneratorAdapter generator, final LwComponent lwComponent) {
    final GridConstraints constraints = lwComponent.getConstraints();

    generator.newInstance(ourCellConstraintsType);
    generator.dup();
    generator.push(constraints.getColumn()+1);
    generator.push(constraints.getRow()+1);
    generator.push(constraints.getColSpan());
    generator.push(constraints.getRowSpan());

    int hAlign = Utils.alignFromConstraints(constraints, true);
    generator.getStatic(ourCellConstraintsType, HORZ_ALIGN_FIELDS[hAlign], ourCellAlignmentType);
    int vAlign = Utils.alignFromConstraints(constraints, false);
    generator.getStatic(ourCellConstraintsType, VERT_ALIGN_FIELDS[vAlign], ourCellAlignmentType);

    generator.invokeConstructor(ourCellConstraintsType, ourCellConstraintsConstructor);
  }
}

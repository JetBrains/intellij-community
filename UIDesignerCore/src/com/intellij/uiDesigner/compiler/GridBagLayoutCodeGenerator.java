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

import com.intellij.uiDesigner.core.Spacer;
import com.intellij.uiDesigner.lw.LwComponent;
import com.intellij.uiDesigner.lw.LwContainer;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * @author yole
 */
public class GridBagLayoutCodeGenerator extends LayoutCodeGenerator {
  private static Type ourGridBagLayoutType = Type.getType(GridBagLayout.class);
  private static Type ourGridBagConstraintsType = Type.getType(GridBagConstraints.class);
  private static Method ourDefaultConstructor = Method.getMethod("void <init> ()");

  private Map myIdToConstraintsMap = new HashMap();
  private static Type myPanelType = Type.getType(JPanel.class);

  public String mapComponentClass(final String componentClassName) {
    if (componentClassName.equals(Spacer.class.getName())) {
      return JPanel.class.getName();
    }
    return super.mapComponentClass(componentClassName);
  }

  public void generateContainerLayout(final LwContainer lwContainer, final GeneratorAdapter generator, final int componentLocal) {
    if (lwContainer.isGrid()) {
      generator.loadLocal(componentLocal);

      generator.newInstance(ourGridBagLayoutType);
      generator.dup();
      generator.invokeConstructor(ourGridBagLayoutType, ourDefaultConstructor);

      generator.invokeVirtual(ourContainerType, ourSetLayoutMethod);

      GridBagConverter.prepareConstraints(lwContainer, myIdToConstraintsMap);
    }
  }

  private void generateFillerPanel(final GeneratorAdapter generator, final int parentLocal, final GridBagConverter.Result result) {
    int panelLocal = generator.newLocal(myPanelType);

    generator.newInstance(myPanelType);
    generator.dup();
    generator.invokeConstructor(myPanelType, ourDefaultConstructor);
    generator.storeLocal(panelLocal);

    generateConversionResult(generator, result, panelLocal, parentLocal);

  }

  public void generateComponentLayout(final LwComponent lwComponent,
                                      final GeneratorAdapter generator,
                                      final int componentLocal,
                                      final int parentLocal) {
    GridBagConverter.Result result = (GridBagConverter.Result) myIdToConstraintsMap.get(lwComponent.getId());
    if (result != null) {
      generateConversionResult(generator, result, componentLocal, parentLocal);
    }
  }

  private static void generateConversionResult(final GeneratorAdapter generator, final GridBagConverter.Result result,
                                               final int componentLocal, final int parentLocal) {
    checkSetSize(generator, componentLocal, "setMinimumSize", result.minimumSize);
    checkSetSize(generator, componentLocal, "setPreferredSize", result.preferredSize);
    checkSetSize(generator, componentLocal, "setMaximumSize", result.maximumSize);

    int gbcLocal = generator.newLocal(ourGridBagConstraintsType);

    generator.newInstance(ourGridBagConstraintsType);
    generator.dup();
    generator.invokeConstructor(ourGridBagConstraintsType, ourDefaultConstructor);
    generator.storeLocal(gbcLocal);

    GridBagConstraints defaults = new GridBagConstraints();
    GridBagConstraints constraints = result.constraints;
    if (defaults.gridx != constraints.gridx) {
      setIntField(generator, gbcLocal, "gridx", constraints.gridx);
    }
    if (defaults.gridy != constraints.gridy) {
      setIntField(generator, gbcLocal, "gridy", constraints.gridy);
    }
    if (defaults.gridwidth != constraints.gridwidth) {
      setIntField(generator, gbcLocal, "gridwidth", constraints.gridwidth);
    }
    if (defaults.gridheight != constraints.gridheight) {
      setIntField(generator, gbcLocal, "gridheight", constraints.gridheight);
    }
    if (defaults.weightx != constraints.weightx) {
      setDoubleField(generator, gbcLocal, "weightx", constraints.weightx);
    }
    if (defaults.weighty != constraints.weighty) {
      setDoubleField(generator, gbcLocal, "weighty", constraints.weighty);
    }
    if (defaults.anchor != constraints.anchor) {
      setIntField(generator, gbcLocal, "anchor", constraints.anchor);
    }
    if (defaults.fill != constraints.fill) {
      setIntField(generator, gbcLocal, "fill", constraints.fill);
    }
    if (defaults.ipadx != constraints.ipadx) {
      setIntField(generator, gbcLocal, "ipadx", constraints.ipadx);
    }
    if (defaults.ipady != constraints.ipady) {
      setIntField(generator, gbcLocal, "ipady", constraints.ipady);
    }
    if (!defaults.insets.equals(constraints.insets)) {
      generator.loadLocal(gbcLocal);
      AsmCodeGenerator.pushPropValue(generator, Insets.class.getName(), constraints.insets);
      generator.putField(ourGridBagConstraintsType, "insets", Type.getType(Insets.class));
    }

    generator.loadLocal(parentLocal);
    generator.loadLocal(componentLocal);
    generator.loadLocal(gbcLocal);

    generator.invokeVirtual(ourContainerType, ourAddMethod);
  }

  private static void checkSetSize(final GeneratorAdapter generator, final int componentLocal, final String methodName, final Dimension dimension) {
    if (dimension != null) {
      generator.loadLocal(componentLocal);
      AsmCodeGenerator.pushPropValue(generator, "java.awt.Dimension", dimension);
      generator.invokeVirtual(Type.getType(Component.class),
                              new Method(methodName, Type.VOID_TYPE, new Type[] { Type.getType(Dimension.class) }));
    }
  }

  private static void setIntField(final GeneratorAdapter generator, final int local, final String fieldName, final int value) {
    generator.loadLocal(local);
    generator.push(value);
    generator.putField(ourGridBagConstraintsType, fieldName, Type.INT_TYPE);
  }

  private static void setDoubleField(final GeneratorAdapter generator, final int local, final String fieldName, final double value) {
    generator.loadLocal(local);
    generator.push(value);
    generator.putField(ourGridBagConstraintsType, fieldName, Type.DOUBLE_TYPE);
  }
}

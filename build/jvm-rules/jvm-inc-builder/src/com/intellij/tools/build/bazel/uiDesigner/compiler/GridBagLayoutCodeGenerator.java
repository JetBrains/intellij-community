// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.uiDesigner.compiler;

import com.intellij.tools.build.bazel.uiDesigner.lw.LwComponent;
import com.intellij.tools.build.bazel.uiDesigner.lw.LwContainer;
import org.jetbrains.org.objectweb.asm.Type;
import org.jetbrains.org.objectweb.asm.commons.GeneratorAdapter;
import org.jetbrains.org.objectweb.asm.commons.Method;

import javax.swing.*;
import java.awt.*;

public final class GridBagLayoutCodeGenerator extends LayoutCodeGenerator {
  private static final Type ourGridBagLayoutType = Type.getType(GridBagLayout.class);
  private static final Type ourGridBagConstraintsType = Type.getType(GridBagConstraints.class);
  private static final Method ourDefaultConstructor = Method.getMethod("void <init> ()");

  @Override
  public String mapComponentClass(final String componentClassName) {
    if ("com.intellij.uiDesigner.core.Spacer".equals(componentClassName)) {
      return JPanel.class.getName();
    }
    return super.mapComponentClass(componentClassName);
  }

  @Override
  public void generateContainerLayout(final LwContainer lwContainer, final GeneratorAdapter generator, final int componentLocal) {
    generator.loadLocal(componentLocal);

    generator.newInstance(ourGridBagLayoutType);
    generator.dup();
    generator.invokeConstructor(ourGridBagLayoutType, ourDefaultConstructor);

    generator.invokeVirtual(ourContainerType, ourSetLayoutMethod);
  }

  @Override
  public void generateComponentLayout(final LwComponent component,
                                      final GeneratorAdapter generator,
                                      final int componentLocal,
                                      final int parentLocal,
                                      final String formClassName) {
    GridBagConstraints gbc;
    if (component.getCustomLayoutConstraints() instanceof GridBagConstraints) {
      gbc = (GridBagConstraints) component.getCustomLayoutConstraints();
    }
    else {
      gbc = new GridBagConstraints();
    }

    GridBagConverter.constraintsToGridBag(component.getConstraints(), gbc);

    generateGridBagConstraints(generator, gbc, componentLocal, parentLocal);
  }

  private static void generateGridBagConstraints(final GeneratorAdapter generator, GridBagConstraints constraints, final int componentLocal,
                                                 final int parentLocal) {
    int gbcLocal = generator.newLocal(ourGridBagConstraintsType);

    generator.newInstance(ourGridBagConstraintsType);
    generator.dup();
    generator.invokeConstructor(ourGridBagConstraintsType, ourDefaultConstructor);
    generator.storeLocal(gbcLocal);

    GridBagConstraints defaults = new GridBagConstraints();
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

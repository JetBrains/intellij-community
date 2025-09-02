// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.generation;

import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.render.RenderingUtil;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import static com.intellij.codeInspection.options.OptMultiSelector.OptElement;

public interface ClassMember extends MemberChooserObject, OptElement {
  ClassMember[] EMPTY_ARRAY = new ClassMember[0];

  /**
   * @return should override equals() and hashCode()
   */
  MemberChooserObject getParentNodeDelegate();

  /**
   * Adapt {@link OptElement} to {@link ClassMember}
   * @param element element to adapt
   * @return input element if it implements {@link ClassMember}, or adapter otherwise
   */
  static @NotNull ClassMember from(OptElement element) {
    if (element instanceof ClassMember member) return member;
    return new ClassMember() {
      @Override
      public MemberChooserObject getParentNodeDelegate() {
        return null;
      }

      @Override
      public void renderTreeNode(SimpleColoredComponent component, JTree tree) {
        SimpleTextAttributes attributes = new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, RenderingUtil.getForeground(tree));
        SpeedSearchUtil.appendFragmentsForSpeedSearch(tree, getText(), attributes, false, component);
      }

      @Override
      public @NotNull String getText() {
        return element.getText();
      }
    };
  }
}

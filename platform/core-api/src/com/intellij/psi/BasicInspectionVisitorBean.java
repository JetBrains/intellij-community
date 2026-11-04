// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.RequiredElement;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.Set;

/**
 * Registers a base visitor class for a language to speed up inspection runs.
 * <p>
 * The optimizer starts with the visitor returned by {@link com.intellij.codeInspection.LocalInspectionTool#buildVisitor}.
 * It examines the visitor's runtime class and intermediate superclasses, then stops before the registered base class.
 * With multiple registered classes, the optimizer stops at the nearest one. Registration order does not matter.
 * It infers accepted PSI types from the parameter types of their {@code visitXxx(...)} methods.
 * The base class's methods do not contribute to the inferred types. Its visit methods must not contain inspection logic.
 * <p>
 * For example, register the Java base visitor in {@code plugin.xml}:
 * <pre>{@code
 * <extensions defaultExtensionNs="com.intellij">
 *   <inspection.basicVisitor class="com.intellij.psi.JavaElementVisitor"/>
 * </extensions>
 * }</pre>
 * A subclass that overrides only {@code visitMethod(PsiMethod)} then receives only elements that implement {@code PsiMethod}.
 * The optimizer calls {@link PsiElement#accept(PsiElementVisitor)} only on matching elements.
 * <p>
 * For inferred types, overriding {@link PsiElementVisitor#visitElement(PsiElement)} requests all elements.
 * The optimizer also visits all elements when no registered base class occurs in the visitor hierarchy.
 * Visitors that implement {@link HintedPsiElementVisitor} provide their accepted PSI types directly.
 *
 * @see com.intellij.codeInsight.daemon.impl.InspectionVisitorOptimizer
 */
@ApiStatus.Experimental
public final class BasicInspectionVisitorBean {

  /**
   * Fully qualified name of the base class that extends {@link PsiElementVisitor}.
   */
  @Attribute("class")
  @RequiredElement
  public String clazz;

  private static final ExtensionPointName<BasicInspectionVisitorBean> EP_NAME =
    ExtensionPointName.create("com.intellij.inspection.basicVisitor");

  private static volatile Set<String> ourClasses;

  static {
    EP_NAME.addChangeListener(() -> {
      ourClasses = null;
    }, null);
  }

  /**
   * Returns the registered base visitor class names.
   */
  public static @NotNull @Unmodifiable Collection<String> getVisitorClasses() {
    Set<String> set = ourClasses;
    if (set != null) return set;

    set = ContainerUtil.map2Set(EP_NAME.getExtensionList(), x -> x.clazz);
    ourClasses = set;

    return set;
  }
}

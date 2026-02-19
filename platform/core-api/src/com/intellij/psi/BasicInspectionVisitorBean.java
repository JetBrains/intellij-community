// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.RequiredElement;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.Set;

import static java.util.stream.Collectors.toSet;

/**
 * Declares a base visitor class for language to speed up inspection runs.
 * <p>
 * When a {@link com.intellij.codeInspection.LocalInspectionTool}
 * returns inheritors of the declared visitor, then the inspection engine will be able
 * to infer PSI element classes that it would like to visit.
 * This makes it possible to skip irrelevant elements in a tree when inspections run.
 *
 * @see com.intellij.codeInsight.daemon.impl.InspectionVisitorOptimizer
 */
@ApiStatus.Experimental
public final class BasicInspectionVisitorBean {

  /**
   * {@link PsiElementVisitor} inheritor visitor class.
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

  public static @NotNull @Unmodifiable Collection<String> getVisitorClasses() {
    Set<String> set = ourClasses;
    if (set != null) return set;

    set = EP_NAME.getExtensionList().stream()
      .map(x -> x.clazz)
      .collect(toSet());
    ourClasses = set;

    return set;
  }
}

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.externalAnnotation;

import com.intellij.codeInsight.ExternalAnnotationsManager;
import com.intellij.codeInsight.intention.AddAnnotationModCommandAction;
import com.intellij.codeInsight.intention.impl.AnnotateIntentionAction;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;

/**
 * {@link AnnotationProvider}s are collected in {@link AnnotateIntentionAction} to form annotations list to be suggested to the user on library code. 
 *  When user chooses an annotation, it will be added externally and stored in xml file in library's external annotations root.
 * <p/>
 * For example, @Nullable/@NotNull annotations on parameters or @Deprecated annotation on method
 * to get warnings in the project code when corresponding methods are used in inappropriate way
 */
public interface AnnotationProvider {
  ExtensionPointName<AnnotationProvider> KEY = ExtensionPointName.create("com.intellij.java.externalAnnotation");

  /**
   * @return annotation name to be shown to the user
   */
  @NotNull
  @NlsSafe
  String getName(Project project);

  /**
   * @return true if annotation is applicable to the {@code owner}
   */
  boolean isAvailable(PsiModifierListOwner owner);

  /**
   * @return array of annotations which can't appear together with the selected annotation e.g., 
   *         for NotNull annotation, having Nullable annotation at the same time makes no sense
   */
  default String @NotNull [] getAnnotationsToRemove(Project project) {
    return ArrayUtilRt.EMPTY_STRING_ARRAY;
  }

  /**
   * @param owner annotation target
   * @return a fix that could be used to add the annotation provided by this provider
   */
  default @NotNull ModCommandAction createFix(@NotNull PsiModifierListOwner owner) {
    Project project = owner.getProject();
    return new AddAnnotationModCommandAction(getName(project), owner, getAnnotationsToRemove(project));
  }

  /**
   * @param owner annotation target
   * @param place where to add the annotation
   * @return a fix that could be used to add the annotation provided by this provider
   */
  default @NotNull ModCommandAction createFix(@NotNull PsiModifierListOwner owner, @NotNull ExternalAnnotationsManager.AnnotationPlace place) {
    Project project = owner.getProject();
    String name = getName(project);
    return new AddAnnotationModCommandAction(name, owner, PsiNameValuePair.EMPTY_ARRAY, place, name, getAnnotationsToRemove(project));
  }
}

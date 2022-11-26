// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.util.JavaElementKind;
import com.intellij.ui.IconManager;
import com.intellij.ui.PlatformIcons;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author ven
*/
public enum CreateClassKind implements ClassKind {
  CLASS(JavaElementKind.CLASS),
  INTERFACE(JavaElementKind.INTERFACE),
  ENUM(JavaElementKind.ENUM),
  ANNOTATION(JavaElementKind.ANNOTATION),
  RECORD(JavaElementKind.RECORD);
  
  private final JavaElementKind myKind;

  CreateClassKind(JavaElementKind kind) {
    myKind = kind;
  }

  @Override
  public @Nls String getDescription() {
    return myKind.subject();
  }

  @Override
  public String getDescriptionAccusative() {
    return myKind.object();
  }

  public @NotNull Icon getKindIcon() {
    IconManager iconManager = IconManager.getInstance();
    return switch (this) {
      case CLASS -> iconManager.getPlatformIcon(PlatformIcons.Class);
      case INTERFACE -> iconManager.getPlatformIcon(PlatformIcons.Interface);
      case ENUM -> iconManager.getPlatformIcon(PlatformIcons.Enum);
      case ANNOTATION -> iconManager.getPlatformIcon(PlatformIcons.Annotation);
      case RECORD -> iconManager.getPlatformIcon(PlatformIcons.Record);
    };
  }

  /**
   * Creates a non-physical class
   * @param factory factory to use
   * @param name name of the new class
   * @return newly created class
   */
  public @NotNull PsiClass create(PsiElementFactory factory, String name) {
    return switch (this) {
      case CLASS -> factory.createClass(name);
      case INTERFACE -> factory.createInterface(name);
      case ENUM -> factory.createEnum(name);
      case ANNOTATION -> factory.createAnnotationType(name);
      case RECORD -> factory.createRecord(name);
    };
  }

  /**
   * Creates a new physical class in directory
   * @param directory directory to create the class at
   * @param name name of the new class
   * @return newly created class
   */
  public @NotNull PsiClass createInDirectory(PsiDirectory directory, String name) {
    JavaDirectoryService service = JavaDirectoryService.getInstance();
    return switch (this) {
      case INTERFACE -> service.createInterface(directory, name);
      case CLASS -> service.createClass(directory, name);
      case ENUM -> service.createEnum(directory, name);
      case RECORD -> service.createRecord(directory, name);
      case ANNOTATION -> service.createAnnotationType(directory, name);
    };
  }
}

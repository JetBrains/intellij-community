/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.util.JavaElementKind;
import com.intellij.util.PlatformIcons;
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

  @NotNull
  public Icon getKindIcon() {
    switch (this) {
      case CLASS:      return PlatformIcons.CLASS_ICON;
      case INTERFACE:  return PlatformIcons.INTERFACE_ICON;
      case ENUM:       return PlatformIcons.ENUM_ICON;
      case ANNOTATION: return PlatformIcons.ANNOTATION_TYPE_ICON;
      case RECORD:     return PlatformIcons.RECORD_ICON;
      default:         throw new IllegalStateException("Unexpected value: " + this);
    }
  }

  /**
   * Creates a non-physical class
   * @param factory factory to use
   * @param name name of the new class
   * @return newly created class
   */
  @NotNull
  public PsiClass create(PsiElementFactory factory, String name) {
    switch (this) {
      case CLASS:      return factory.createClass(name);
      case INTERFACE:  return factory.createInterface(name);
      case ENUM:       return factory.createEnum(name);
      case ANNOTATION: return factory.createAnnotationType(name);
      case RECORD:     return factory.createRecord(name);
      default:         throw new IllegalStateException("Unexpected value: " + this);
    }
  }

  /**
   * Creates a new physical class in directory
   * @param directory directory to create the class at
   * @param name name of the new class
   * @return newly created class
   */
  @NotNull
  public PsiClass createInDirectory(PsiDirectory directory, String name) {
    JavaDirectoryService service = JavaDirectoryService.getInstance();
    switch (this) {
      case INTERFACE:  return service.createInterface(directory, name);
      case CLASS:      return service.createClass(directory, name);
      case ENUM:       return service.createEnum(directory, name);
      case RECORD:     return service.createRecord(directory, name);
      case ANNOTATION: return service.createAnnotationType(directory, name);
      default:         throw new IllegalStateException("Unexpected value: " + this);
    }
  }
}

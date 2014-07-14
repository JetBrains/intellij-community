/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.designer.model;

import com.intellij.designer.propertyTable.PropertyEditor;
import com.intellij.designer.propertyTable.PropertyRenderer;
import com.intellij.psi.PsiElement;
import com.intellij.util.ui.TextTransferable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.Transferable;
import java.util.Collections;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public abstract class Property<T extends PropertiesContainer> {
  private final Property myParent;
  private final String myName;
  private boolean myImportant;
  private boolean myExpert;
  private boolean myDeprecated;

  public Property(@Nullable Property parent, @NotNull String name) {
    myParent = parent;
    myName = name;
  }

  @Nullable
  public Property<T> createForNewPresentation() {
    return createForNewPresentation(myParent, myName);
  }

  @Nullable
  public Property<T> createForNewPresentation(@Nullable Property parent, @NotNull String name) {
    return null;
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // Hierarchy
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  @Nullable
  public String getGroup() {
    return null;
  }

  @Nullable
  public final Property getParent() {
    return myParent;
  }

  @NotNull
  public List<? extends Property<T>> getChildren(@Nullable T container) {
    return Collections.emptyList();
  }

  @NotNull
  public String getPath() {
    return myParent == null ? myName : myParent.getPath() + "/" + myName;
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // Value
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  @Nullable
  public Object getValue(@NotNull T container) throws Exception {
    return null;
  }

  public void setValue(@NotNull T container, @Nullable Object value) throws Exception {
  }

  public boolean showAsDefault(@NotNull T container) throws Exception {
    return isDefaultRecursively(container);
  }

  public final boolean isDefaultRecursively(@NotNull T container) throws Exception {
    if (!isDefaultValue(container)) return false;
    for (Property<T> child : getChildren(container)) {
      if (!child.isDefaultRecursively(container)) return false;
    }
    return true;
  }

  public boolean isDefaultValue(@NotNull T container) throws Exception {
    return true;
  }

  public void setDefaultValue(@NotNull T container) throws Exception {
  }

  public boolean availableFor(List<PropertiesContainer> components) {
    return true;
  }

  public boolean needRefreshPropertyList(@NotNull T container, @Nullable Object oldValue, @Nullable Object newValue) throws Exception {
    return false;
  }

  public boolean needRefreshPropertyList() {
    return false;
  }

  public boolean closeEditorDuringRefresh() {
    return false;
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // Copy
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  @NotNull
  public Transferable doCopy(@NotNull T container, @NotNull Object commonValue) {
    return new TextTransferable(commonValue.toString());
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // Presentation
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  @NotNull
  public final String getName() {
    return myName;
  }

  @Nullable
  public String getTooltip() {
    return null;
  }

  public boolean isImportant() {
    return myImportant;
  }

  public void setImportant(boolean important) {
    myImportant = important;
  }

  public boolean isExpert() {
    return myExpert;
  }

  public void setExpert(boolean expert) {
    myExpert = expert;
  }

  public boolean isDeprecated() {
    return myDeprecated;
  }

  public void setDeprecated(boolean deprecated) {
    myDeprecated = deprecated;
  }

  @NotNull
  public abstract PropertyRenderer getRenderer();

  @Nullable
  public abstract PropertyEditor getEditor();

  public boolean isEditable(@Nullable T component) {
    return getEditor() != null;
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // Javadoc
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  @Nullable
  public PsiElement getJavadocElement() {
    return null;
  }

  @Nullable
  public String getJavadocText() {
    return null;
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof Property && getPath().equals(((Property) obj).getPath());
  }
}
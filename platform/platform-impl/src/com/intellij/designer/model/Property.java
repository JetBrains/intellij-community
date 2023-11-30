// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.designer.model;

import com.intellij.designer.propertyTable.PropertyEditor;
import com.intellij.designer.propertyTable.PropertyRenderer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiElement;
import com.intellij.util.ui.TextTransferable;
import org.jetbrains.annotations.Nls;
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

  public @Nullable Property<T> createForNewPresentation() {
    return createForNewPresentation(myParent, myName);
  }

  public @Nullable Property<T> createForNewPresentation(@Nullable Property parent, @NotNull String name) {
    return null;
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // Hierarchy
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  public @Nullable String getGroup() {
    return null;
  }

  public final @Nullable Property getParent() {
    return myParent;
  }

  public @NotNull List<? extends Property<T>> getChildren(@Nullable T container) {
    return Collections.emptyList();
  }

  public @NotNull String getPath() {
    return myParent == null ? myName : myParent.getPath() + "/" + myName;
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // Value
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  public @Nullable Object getValue(@NotNull T container) throws Exception {
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

  public @NotNull Transferable doCopy(@NotNull T container, @NotNull Object commonValue) {
    return new TextTransferable(commonValue.toString());
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // Presentation
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  public final @NotNull @NlsSafe String getName() {
    return myName;
  }

  public @Nullable @NlsContexts.Tooltip String getTooltip() {
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

  public abstract @NotNull PropertyRenderer getRenderer();

  public abstract @Nullable PropertyEditor getEditor();

  public boolean isEditable(@Nullable T component) {
    return getEditor() != null;
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // Javadoc
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  public @Nullable PsiElement getJavadocElement() {
    return null;
  }

  public @Nullable @Nls String getJavadocText() {
    return null;
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof Property && getPath().equals(((Property<?>) obj).getPath());
  }
}
// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl.descriptors.data;

import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.ui.impl.watch.UserExpressionDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.debugger.ui.tree.UserExpressionDescriptor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class UserExpressionData extends DescriptorData<UserExpressionDescriptor> {
  private final ValueDescriptorImpl myParentDescriptor;
  private final String myTypeName;
  private final String myName;
  protected TextWithImports myText;
  private int myEnumerationIndex = -1;

  public UserExpressionData(ValueDescriptorImpl parentDescriptor, String typeName, String name, TextWithImports text) {
    super();
    myParentDescriptor = parentDescriptor;
    myTypeName = typeName;
    myName = name;
    myText = text;
  }

  @Override
  protected UserExpressionDescriptorImpl createDescriptorImpl(@NotNull Project project) {
    return new UserExpressionDescriptorImpl(project, myParentDescriptor, myTypeName, myName, myText, myEnumerationIndex);
  }

  @Override
  public boolean equals(Object object) {
    if (!(object instanceof UserExpressionData)) return false;

    return myName.equals(((UserExpressionData)object).myName);
  }

  @Override
  public int hashCode() {
    return myName.hashCode();
  }

  @Override
  public DisplayKey<UserExpressionDescriptor> getDisplayKey() {
    return new SimpleDisplayKey<>(myTypeName + myName);
  }

  public void setEnumerationIndex(int enumerationIndex) {
    myEnumerationIndex = enumerationIndex;
  }
}

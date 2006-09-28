package com.intellij.debugger.impl.descriptors.data;

import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.ui.impl.watch.UserExpressionDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.debugger.ui.tree.UserExpressionDescriptor;
import com.intellij.openapi.project.Project;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public class UserExpressionData extends DescriptorData<UserExpressionDescriptor>{
  private final ValueDescriptorImpl myParentDescriptor;
  private final String myTypeName;
  private final String myName;
  protected TextWithImports myText;

  public UserExpressionData(ValueDescriptorImpl parentDescriptor, String typeName, String name, TextWithImports text) {
    super();
    myParentDescriptor = parentDescriptor;
    myTypeName = typeName;
    myName = name;
    myText = text;
  }

  protected UserExpressionDescriptorImpl createDescriptorImpl(Project project) {
    return new UserExpressionDescriptorImpl(project, myParentDescriptor, myTypeName, myName, myText);
  }

  public boolean equals(Object object) {
    if(!(object instanceof UserExpressionData)) return false;

    return myName.equals(((UserExpressionData)object).myName);
  }

  public int hashCode() {
    return myName.hashCode();
  }

  public DisplayKey<UserExpressionDescriptor> getDisplayKey() {
    return new SimpleDisplayKey<UserExpressionDescriptor>(myTypeName + myName);
  }
}

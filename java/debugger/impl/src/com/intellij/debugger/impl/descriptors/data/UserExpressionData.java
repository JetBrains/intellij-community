/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.debugger.impl.descriptors.data;

import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.ui.impl.watch.UserExpressionDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.debugger.ui.tree.UserExpressionDescriptor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class UserExpressionData extends DescriptorData<UserExpressionDescriptor>{
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

  protected UserExpressionDescriptorImpl createDescriptorImpl(@NotNull Project project) {
    return new UserExpressionDescriptorImpl(project, myParentDescriptor, myTypeName, myName, myText, myEnumerationIndex);
  }

  public boolean equals(Object object) {
    if(!(object instanceof UserExpressionData)) return false;

    return myName.equals(((UserExpressionData)object).myName);
  }

  public int hashCode() {
    return myName.hashCode();
  }

  public DisplayKey<UserExpressionDescriptor> getDisplayKey() {
    return new SimpleDisplayKey<>(myTypeName + myName);
  }

  public void setEnumerationIndex(int enumerationIndex) {
    myEnumerationIndex = enumerationIndex;
  }
}

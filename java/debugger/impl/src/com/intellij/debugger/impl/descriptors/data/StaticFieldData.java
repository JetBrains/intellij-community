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

import com.intellij.debugger.ui.impl.watch.FieldDescriptorImpl;
import com.intellij.openapi.project.Project;
import com.sun.jdi.Field;
import org.jetbrains.annotations.NotNull;

public final class StaticFieldData extends DescriptorData<FieldDescriptorImpl>{
  private final Field myField;

  public StaticFieldData(@NotNull Field field) {
    myField = field;
  }

  protected FieldDescriptorImpl createDescriptorImpl(@NotNull Project project) {
    return new FieldDescriptorImpl(project, null, myField);
  }

  public boolean equals(Object object) {
    if(!(object instanceof StaticFieldData)) {
      return false;
    }
    final StaticFieldData fieldData = (StaticFieldData)object;
    return (fieldData.myField == myField);
  }

  public int hashCode() {
    return myField.hashCode();
  }

  public DisplayKey<FieldDescriptorImpl> getDisplayKey() {
    return new SimpleDisplayKey<>(myField);
  }
}

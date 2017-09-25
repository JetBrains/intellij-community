/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

package com.intellij.execution.impl;

import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.BeforeRunTaskProvider;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.Key;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: Sep 15, 2009
 */
public class UnknownBeforeRunTaskProvider extends BeforeRunTaskProvider<UnknownBeforeRunTaskProvider.UnknownTask> {
  private final Key<UnknownTask> myId;

  public UnknownBeforeRunTaskProvider(String mirrorProviderName) {
    myId = Key.create(mirrorProviderName);
  }

  @Override
  public Key<UnknownTask> getId() {
    return myId;
  }

  @Override
  public String getName() {
    return ExecutionBundle.message("before.launch.run.unknown.task");
  }

  @Override
  public String getDescription(UnknownTask task) {
    return ExecutionBundle.message("before.launch.run.unknown.task") + " " + myId.toString();
  }

  @Override
  public boolean configureTask(@NotNull RunConfiguration runConfiguration, @NotNull UnknownTask task) {
    return false;
  }

  @Override
  public boolean canExecuteTask(@NotNull RunConfiguration configuration, @NotNull UnknownTask task) {
    return false;
  }

  @Override
  public boolean executeTask(DataContext context, @NotNull RunConfiguration configuration, @NotNull ExecutionEnvironment env, @NotNull UnknownTask task) {
    return true;
  }

  @Override
  public UnknownTask createTask(@NotNull RunConfiguration runConfiguration) {
    return new UnknownTask(getId());
  }

  public static final class UnknownTask extends BeforeRunTask<UnknownTask> {
    private Element myConfig;

    public UnknownTask(Key<UnknownTask> providerId) {
      super(providerId);
    }

    @Override
    public void readExternal(@NotNull Element element) {
      myConfig = element;
    }

    @Override
    public void writeExternal(@NotNull Element element) {
      if (myConfig != null) {
        element.removeContent();
        final List attributes = myConfig.getAttributes();
        for (Object attribute : attributes) {
         element.setAttribute(((Attribute)attribute).clone());
        }
        for (Object child : myConfig.getChildren()) {
          element.addContent(((Element)child).clone());
        }
      }
    }

    @Override
    public BeforeRunTask clone() {
      return super.clone();
    }

    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      if (!super.equals(o)) return false;

      UnknownTask that = (UnknownTask)o;

      if (!JDOMUtil.areElementsEqual(myConfig, that.myConfig)) return false;

      return true;
    }

    public int hashCode() {
      int result = super.hashCode();
      result = 31 * result + (myConfig != null ? myConfig.hashCode() : 0);
      return result;
    }
  }
}

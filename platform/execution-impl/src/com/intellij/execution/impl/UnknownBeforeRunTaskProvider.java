// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

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
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Eugene Zhuravlev
 */
@ApiStatus.Internal
public final class UnknownBeforeRunTaskProvider extends BeforeRunTaskProvider<UnknownBeforeRunTaskProvider.UnknownTask> {
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
    return ExecutionBundle.message("before.launch.run.unknown.task") + " " + myId.toString(); //NON-NLS
  }

  @Override
  public boolean canExecuteTask(@NotNull RunConfiguration configuration, @NotNull UnknownTask task) {
    return false;
  }

  @Override
  public boolean executeTask(@NotNull DataContext context, @NotNull RunConfiguration configuration, @NotNull ExecutionEnvironment env, @NotNull UnknownTask task) {
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
        final List<Attribute> attributes = myConfig.getAttributes();
        for (Attribute attribute : attributes) {
         element.setAttribute(attribute.clone());
        }
        for (Element child : myConfig.getChildren()) {
          element.addContent(child.clone());
        }
      }
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
      result = 31 * result + JDOMUtil.hashCode(myConfig, false);
      return result;
    }
  }
}

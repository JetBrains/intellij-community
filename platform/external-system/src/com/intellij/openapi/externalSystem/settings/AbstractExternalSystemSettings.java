/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.externalSystem.settings;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.util.messages.Topic;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Common base class for external system settings. Defines a minimal api which is necessary for the common external system
 * support codebase.
 * <p/>
 * <b>Note:</b> non-abstract sub-classes of this class are expected to be marked by {@link State} annotation configured as necessary.
 *  
 * @author Denis Zhdanov
 * @since 4/3/13 4:04 PM
 */
public abstract class AbstractExternalSystemSettings<L extends ExternalSystemSettingsListener, S extends AbstractExternalSystemSettings<L,S>>
  implements PersistentStateComponent<S>
{

  @NotNull private final Topic<L> myChangesTopic;
  @NotNull private final Project  myProject;

  @Nullable private String myLinkedProjectPath;

  private boolean myUseAutoImport = true; // Turned on by default.

  protected AbstractExternalSystemSettings(@NotNull Topic<L> topic, @NotNull Project project) {
    myChangesTopic = topic;
    myProject = project;
  }

  @SuppressWarnings("unchecked")
  @Nullable
  @Override
  public S getState() {
    return (S)this;
  }

  @Override
  public void loadState(S state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  @Nullable
  public String getLinkedProjectPath() {
    return myLinkedProjectPath;
  }

  @SuppressWarnings("UnusedDeclaration")
  public void setLinkedProjectPath(@Nullable String linkedProjectPath) {
    if (!Comparing.equal(myLinkedProjectPath, linkedProjectPath)) {
      final String oldPath = myLinkedProjectPath;
      myLinkedProjectPath = linkedProjectPath;
      myProject.getMessageBus().syncPublisher(myChangesTopic).onLinkedProjectPathChange(oldPath, linkedProjectPath);
    }
  }

  public boolean isUseAutoImport() {
    return myUseAutoImport;
  }

  public void setUseAutoImport(boolean useAutoImport) {
    if (myUseAutoImport != useAutoImport) {
      myUseAutoImport = useAutoImport;
      myProject.getMessageBus().syncPublisher(myChangesTopic).onUseAutoImportChange(useAutoImport);
    }
  }

  @NotNull
  protected L getPublisher() {
    return myProject.getMessageBus().syncPublisher(myChangesTopic);
  }
}

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
package com.intellij.openapi.externalSystem.service.project.change;

import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.change.ExternalProjectStructureChange;
import com.intellij.openapi.externalSystem.model.project.change.user.UserProjectChange;
import com.intellij.openapi.externalSystem.service.project.manage.EntityManageHelper;
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemSettings;
import com.intellij.openapi.externalSystem.settings.ExternalSystemSettingsManager;
import com.intellij.openapi.externalSystem.settings.UserProjectChanges;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Automates a task of keeping gradle and ide projects in sync,
 * <p/>
 * The general idea is to import all gradle-local entities as soon as they are detected. However, it doesn't that simple.
 * Consider a situation when a user, say, renames a module (gradle sub-project). We would like not only import new module
 * but remove the old one as well. Unfortunately, we don't have api for distinguishing change like 'gradle sub-project remove'
 * from 'new module is added to the project by a user'. They both look like 'new ide-local module'. That's why we use the following
 * algorithm for every project structure change here:
 * <pre>
 * <ol>
 *   <li>
 *     Check if there is an {@link UserProjectChange explicitly made project structure change} for the current project
 *     structure change;
 *   </li>
 *   <li>
 *     {@link EntityManageHelper#eliminateChange(Project, Collection, Set, boolean) Resolve the change} if it doesn't occur
 *     because of user actions (e.g. we don't want to auto-remove a module which is absent at gradle but presents at IDE);
 *   </li>
 * </ol>
 * </pre>
 * 
 * @author Denis Zhdanov
 * @since 2/18/13 7:55 PM
 */
public class AutoImporter implements ExternalProjectStructureChangesPostProcessor {

  @NotNull private final AtomicBoolean myInProgress = new AtomicBoolean();

  @NotNull private final ExternalSystemSettingsManager mySettingsManager;
  @NotNull private final EntityManageHelper            myEntityManageHelper;

  public AutoImporter(@NotNull ExternalSystemSettingsManager manager, @NotNull EntityManageHelper helper) {
    mySettingsManager = manager;
    myEntityManageHelper = helper;
  }

  public boolean isInProgress() {
    return myInProgress.get();
  }

  @SuppressWarnings("unchecked")
  @Override
  public void processChanges(@NotNull Collection<ExternalProjectStructureChange> changes,
                             @NotNull ProjectSystemId externalSystemId,
                             @NotNull Project project,
                             boolean onIdeProjectStructureChange)
  {
    if (onIdeProjectStructureChange) {
      return;
    }
    AbstractExternalSystemSettings<?, ?> settings = mySettingsManager.getSettings(project, externalSystemId);
    if (!settings.isUseAutoImport() || StringUtil.isEmpty(settings.getLinkedExternalProjectPath())) {
      return;
    }

    Set<ExternalProjectStructureChange> nonProcessed;
    myInProgress.set(true);
    try {
      Set<UserProjectChange<?>> userChanges = UserProjectChanges.getInstance(project).getUserProjectChanges();
      nonProcessed = myEntityManageHelper.eliminateChange(project, changes, userChanges, true);
    }
    finally {
      myInProgress.set(false);
    }
    changes.clear();
    changes.addAll(nonProcessed);
  }
}

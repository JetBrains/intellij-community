/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.openapi.localVcs;

import com.intellij.openapi.components.SettingsSavingComponent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

/**
 * @author Mike
 */
public abstract class LocalVcs implements SettingsSavingComponent {
  public static LocalVcs getInstance(Project project) {
    return project.getComponent(LocalVcs.class);
  }

  public abstract Project getProject();

  public abstract String[] getRootPaths();

  @Nullable
  public abstract LvcsFile findFile(String filePath);

  @Nullable
  public abstract LvcsFile findFile(String filePath, boolean ignoreDeleted);

  @Nullable
  public abstract LvcsFileRevision findFileRevisionByDate(final String filePath, long date);

  @Nullable
  public abstract LvcsFile findFile(String filePath, LvcsLabel label);

  @Nullable
  public abstract LvcsDirectory findDirectory(String dirPath);

  @Nullable
  public abstract LvcsDirectory findDirectory(String dirPath, boolean ignoreDeleted);

  @Nullable
  public abstract LvcsDirectory findDirectory(String dirPath, LvcsLabel label);

  public abstract LvcsLabel addLabel(String name, String path);

  public abstract LvcsLabel addLabel(byte type, String name, String path);

  public abstract LvcsAction startAction(String action, String path, boolean isExternalChanges);

  public abstract LvcsRevision[] getChanges(String path, LvcsLabel label);

  public abstract LvcsRevision[] getChanges(LvcsLabel label1, LvcsLabel label2);

  public abstract boolean isUnderVcs(VirtualFile file);

  public abstract boolean isAvailable();

  public abstract LocalVcsPurgingProvider getLocalVcsPurgingProvider();

  public abstract void markSourcesAsCurrent(String label);

  public abstract void markModuleSourcesAsCurrent(Module module, String label);

  public abstract LvcsLabel[] getAllLabels();

  public abstract boolean rollbackToLabel(LvcsLabel label, boolean requestConfirmation, String confirmationMessage, String confirmationTitle);

  public abstract boolean rollbackToLabel(LvcsLabel label, boolean requestConfirmation);

  public abstract void addLvcsLabelListener(LvcsLabelListener listener);

  public abstract void removeLvcsLabelListener(LvcsLabelListener listener);

  public abstract UpToDateLineNumberProvider getUpToDateLineNumberProvider(Document document, String upToDateContent);

  public abstract LvcsConfiguration getConfiguration();
}

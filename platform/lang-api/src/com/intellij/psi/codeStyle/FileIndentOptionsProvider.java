/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.psi.codeStyle;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Rustam Vishnyakov
 */
public abstract class FileIndentOptionsProvider {

  public final static ExtensionPointName<FileIndentOptionsProvider> EP_NAME = ExtensionPointName.create("com.intellij.fileIndentOptionsProvider");

  private final static String SHOW_NOTIFICATION_KEY = "show.indent.detected.notification";
  /**
   * Retrieves indent options for PSI file.
   * @param settings Code style settings for which indent options are calculated.
   * @param file The file to retrieve options for.
   * @return Indent options or <code>null</code> if the provider can't retrieve them.
   */
  @Nullable
  public abstract CommonCodeStyleSettings.IndentOptions getIndentOptions(@NotNull CodeStyleSettings settings, @NotNull PsiFile file);

  /**
   * Tells if the provider can be used when a complete file is reformatted.
   * @return True by default
   */
  public boolean useOnFullReformat() {
    return true;
  }

  /**
   * @return information used to create user notification in editor. If the option is <code>null</code>, no notification
   * will be shown.
   */
  @Nullable
  public EditorNotificationInfo getNotificationInfo(@NotNull Project project,
                                                    @NotNull VirtualFile file,
                                                    @NotNull FileEditor fileEditor,
                                                    @NotNull CommonCodeStyleSettings.IndentOptions user,
                                                    @NotNull CommonCodeStyleSettings.IndentOptions detected) {
    return null;
  }
  
  /**
   * Tells if there should not be any notification for this specific file.
   * @param file  The file to check.
   * @return <code>true</code> if the file can be silently accepted without a warning.
   */
  @SuppressWarnings("UnusedParameters")
  public boolean isAcceptedWithoutWarning(@Nullable Project project, @NotNull VirtualFile file) {
    return false;
  }

  /**
   * Sets the file as accepted by end user.
   * @param file The file to be accepted. A particular implementation of <code>FileIndentOptionsProvider</code> may ignore this parameter
   *             and set a global acceptance flag so that no notification will be shown anymore.
   */
  public void setAccepted(@SuppressWarnings("UnusedParameters") @NotNull VirtualFile file) {}

  public static boolean isShowNotification() {
    return PropertiesComponent.getInstance().getBoolean(SHOW_NOTIFICATION_KEY, true);
  }

  public static void setShowNotification(boolean value) {
    PropertiesComponent.getInstance().setValue(SHOW_NOTIFICATION_KEY, Boolean.toString(value), Boolean.toString(true));
  }
}

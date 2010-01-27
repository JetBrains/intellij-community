/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.find;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Allows to invoke and control Find, Replace and Find Usages operations.
 */
public abstract class FindManager {
  public static final Topic<FindModelListener> FIND_MODEL_TOPIC = new Topic<FindModelListener>("FindManager's model changes",
                                                                                               FindModelListener.class);
  /**
   * Returns the find manager instance for the specified project.
   *
   * @param project the project for which the manager is requested.
   * @return the manager instance.
   */
  public static FindManager getInstance(Project project) {
    return ServiceManager.getService(project, FindManager.class);
  }

  /**
   * Shows the Find, Replace or Find Usages dialog initializing it from the specified
   * model and saves the settings entered by the user into the same model. Does not
   * perform the actual find or replace operation.
   *
   * @param model the model containing the settings of a find or replace operation.
   * @param okHandler Will be executed after doOkAction
   */
  public abstract void showFindDialog(@NotNull FindModel model, @NotNull Runnable okHandler);

  /**
   * Shows a replace prompt dialog for the specfied replace operation.
   *
   * @param model the model containing the settings of the replace operation.
   * @param title the title of the dialog to show.
   * @return the exit code of the dialog, as defined by the {@link PromptResult}
   * interface.
   */
  public abstract int showPromptDialog(FindModel model, String title);

  /**
   * Returns the settings of the last performed Find in File operation, or the
   * default Find in File settings if no such operation was performed by the user.
   *
   * @return the last Find in File settings.
   */
  @NotNull
  public abstract FindModel getFindInFileModel();

  /**
   * Returns the settings of the last performed Find in Project operation, or the
   * default Find in Project settings if no such operation was performed by the user.
   *
   * @return the last Find in Project settings.
   */
  @NotNull
  public abstract FindModel getFindInProjectModel();

  /**
   * Searches for the specified substring in the specified character sequence,
   * using the specified find settings. Supports case sensitive and insensitive
   * searches, forward and backward searches, regular expression searches and
   * searches for whole words.
   *
   * @param text   the text in which the search is performed.
   * @param offset the start offset for the search.
   * @param model  the settings for the search, including the string to find.
   * @return the result of the search.
   */
  @NotNull
  public abstract FindResult findString(@NotNull CharSequence text, int offset, @NotNull FindModel model);

  /**
   * Searches for the specified substring in the specified character sequence,
   * using the specified find settings. Supports case sensitive and insensitive
   * searches, forward and backward searches, regular expression searches and
   * searches for whole words.
   *
   * @param text   the text in which the search is performed.
   * @param offset the start offset for the search.
   * @param model  the settings for the search, including the string to find.
   * @return the result of the search.
   */
  @NotNull
  public abstract FindResult findString(@NotNull CharSequence text, int offset, @NotNull FindModel model,
                                        @Nullable VirtualFile findContextFile);

  /**
   * Gets the string to replace with, given the specified found string and find/replace
   * settings. Supports case-preserving and regular expression replaces.
   *
   * @param foundString the found string.
   * @param model       the search and replace settings, including the replace string.
   * @return the string to replace the specified found string.
   */
  public abstract String getStringToReplace(@NotNull String foundString, @NotNull FindModel model);
  public abstract String getStringToReplace(@NotNull String foundString, @NotNull FindModel model, int startOffset, @NotNull String documentText);

  /**
   * Gets the flag indicating whether the "Find Next" and "Find Previous" actions are
   * available to continue a previously started search operation. (The operations are
   * available if at least one search was performed in the current IDEA session.)
   *
   * @return true if the actions are available, false if there is no previous search
   *         operation to continue.
   */
  public abstract boolean findWasPerformed();

  /**
   * Sets the flag indicating that the "Find Next" and "Find Previous" actions are
   * available to continue a previously started search operation.
   */
  public abstract void setFindWasPerformed();

  /**
   * Sets the model containing the search settings to use for "Find Next" and
   * "Find Previous" operations.
   *
   * @param model the model to use for the operations.
   */
  public abstract void setFindNextModel(FindModel model);

  /**
   * Gets the model containing the search settings to use for "Find Next" and
   * "Find Previous" operations.
   *
   * @return the model to use for the operations.
   */
  public abstract FindModel getFindNextModel();

  /**
   * Gets the model containing the search settings to use for "Find Next" and
   * "Find Previous" operations specific for the editor given. It may be different than {@link #getFindNextModel()}
   * if there is find bar currently shown for the editor.
   *
   * @param editor editor, for which find model shall be retreived for
   * @return the model to use for the operations.
   */
  public abstract FindModel getFindNextModel(@NotNull Editor editor);

  /**
   * Checks if the Find Usages action is available for the specified element.
   *
   * @param element the element to check the availability for.
   * @return true if Find Usages is available, false otherwise.
   * @see com.intellij.lang.findUsages.FindUsagesProvider#canFindUsagesFor(com.intellij.psi.PsiElement)
   */
  public abstract boolean canFindUsages(@NotNull PsiElement element);

  /**
   * Shows the Find Usages dialog and performs the Find Usages operation for the
   * specified element.
   *
   * @param element the element to find the usages for.
   */
  public abstract void findUsages(@NotNull PsiElement element);

  /**
   * Performs a "Find Usages in File" operation for the specified element.
   *
   * @param element the element for which the find is performed.
   * @param editor  the editor in which the find is performed.
   */
  public abstract void findUsagesInEditor(@NotNull PsiElement element, @NotNull FileEditor editor);

  /**
   * Performs a "Find Next" operation after "Find Usages in File" or
   * "Highlight Usages in File".
   *
   * @param editor the editor in which the find is performed.
   * @return true if the operation was performed (not necessarily found anything),
   *         false if an error occurred during the operation.
   */
  public abstract boolean findNextUsageInEditor(@NotNull FileEditor editor);

  /**
   * Performs a "Find Previous" operation after "Find Usages in File" or
   * "Highlight Usages in File".
   *
   * @param editor the editor in which the find is performed.
   * @return true if the operation was performed (not necessarily found anything),
   *         false if an error occurred during the operation.
   */
  public abstract boolean findPreviousUsageInEditor(@NotNull FileEditor editor);

  /**
   * Possible return values for the {@link FindManager#showPromptDialog(FindModel, String)} method.
   *
   * @since 5.0.2
   */
  public interface PromptResult {
    int OK = 0;
    int CANCEL = 1;
    int SKIP = 2;
    int ALL = 3;
    int ALL_IN_THIS_FILE = 4;
    int ALL_FILES = 5;
  }
}

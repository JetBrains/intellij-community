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
package com.intellij.openapi.vcs.checkout;

import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.VcsBundle;

import java.io.File;

/**
 * @author irengrig
 * @since 5/27/11
 */
public class ProjectDirCheckoutListener implements CheckoutListener {
  @Override
  public boolean processCheckedOutDirectory(Project project, File directory) {
    // todo Rider project layout — several .idea.solution-name names
    if (!new File(directory, Project.DIRECTORY_STORE_FOLDER).exists()) {
      return false;
    }

    String message = VcsBundle.message("checkout.open.project.dir.prompt",
                                       ProjectCheckoutListener.getProductNameWithArticle(), directory.getPath());
    if (Messages.showYesNoDialog(project, message, VcsBundle.message("checkout.title"), Messages.getQuestionIcon()) == Messages.YES) {
      ProjectUtil.openProject(directory.getPath(), project, false);
    }
    return true;
  }

  @Override
  public void processOpenedProject(Project lastOpenedProject) {
  }
}

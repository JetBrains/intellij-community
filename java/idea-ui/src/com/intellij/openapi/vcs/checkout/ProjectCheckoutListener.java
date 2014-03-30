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
package com.intellij.openapi.vcs.checkout;

import com.intellij.ide.highlighter.ProjectFileType;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.util.PlatformUtils;
import org.apache.oro.io.GlobFilenameFilter;

import java.io.File;
import java.io.FilenameFilter;

/**
 * @author yole
 */
public class ProjectCheckoutListener implements CheckoutListener {
  @Override
  public boolean processCheckedOutDirectory(Project project, File directory) {
    File[] files = directory.listFiles((FilenameFilter) new GlobFilenameFilter("*" + ProjectFileType.DOT_DEFAULT_EXTENSION));
    if (files != null && files.length > 0) {
      int rc = Messages
        .showYesNoDialog(project, VcsBundle.message("checkout.open.project.prompt", getProductNameWithArticle(), files[0].getPath()),
                         VcsBundle.message("checkout.title"), Messages.getQuestionIcon());
      if (rc == Messages.YES) {
        ProjectUtil.openProject(files[0].getPath(), project, false);
      }
      return true;
    }
    return false;
  }

  @Override
  public void processOpenedProject(Project lastOpenedProject) {
  }

  static String getProductNameWithArticle() {
    final ApplicationNamesInfo namesInfo = ApplicationNamesInfo.getInstance();
    // example: "to create an IntelliJ IDEA project" (full product name is ok);
    // "to create a JetBrains Astella project" (better use not full product name: "to create an Astella project")
    final String productName = PlatformUtils.isIdeaUltimate() ? namesInfo.getFullProductName() : namesInfo.getProductName();
    final String article = StringUtil.isVowel(Character.toLowerCase(productName.charAt(0))) ? "an " : "a ";
    return article + productName;
  }
}
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

/*
 * User: anna
 * Date: 26-Dec-2007
 */
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.roots.ProjectExtension;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.pom.java.LanguageLevel;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

public class LanguageLevelProjectExtensionImpl extends LanguageLevelProjectExtension {
  @Deprecated
  @NonNls private static final String ASSERT_KEYWORD_ATTR = "assert-keyword";
  @Deprecated
  @NonNls private static final String JDK_15_ATTR = "jdk-15";

  private LanguageLevel myLanguageLevel = LanguageLevel.JDK_1_5;
  private LanguageLevel myOriginalLanguageLevel = myLanguageLevel;

  private final Project myProject;
  private volatile Runnable myReloadProjectRequest;

  public LanguageLevelProjectExtensionImpl(final Project project) {
    myProject = project;
  }

  private void readExternal(final Element element) {
    String level = element.getAttributeValue("languageLevel");
    if (level == null) {
      myLanguageLevel = migrateFromIdea7(element);
    }
    else {
      myLanguageLevel = LanguageLevel.valueOf(level);
    }
    myOriginalLanguageLevel = myLanguageLevel;
  }

  private static LanguageLevel migrateFromIdea7(Element element) {
    final boolean assertKeyword = Boolean.valueOf(element.getAttributeValue(ASSERT_KEYWORD_ATTR)).booleanValue();
    final boolean jdk15 = Boolean.valueOf(element.getAttributeValue(JDK_15_ATTR)).booleanValue();
    if (jdk15) {
      return LanguageLevel.JDK_1_5;
    }
    else if (assertKeyword) {
      return LanguageLevel.JDK_1_4;
    }
    else {
      return LanguageLevel.JDK_1_3;
    }
  }

  private void writeExternal(final Element element) {
    element.setAttribute("languageLevel", myLanguageLevel.name());
    writeAttributesForIdea7(element);
  }

  private void writeAttributesForIdea7(Element element) {
    final boolean is14 = LanguageLevel.JDK_1_4.equals(myLanguageLevel);
    final boolean is15 = myLanguageLevel.compareTo(LanguageLevel.JDK_1_5) >= 0;
    element.setAttribute(ASSERT_KEYWORD_ATTR, Boolean.toString(is14 || is15));
    element.setAttribute(JDK_15_ATTR, Boolean.toString(is15));
  }

  public LanguageLevel getLanguageLevel() {
    return myLanguageLevel;
  }

  public void setLanguageLevel(LanguageLevel languageLevel) {
    if (myLanguageLevel != languageLevel) {
      reloadProjectOnLanguageLevelChange(languageLevel, false);
    }
    myLanguageLevel = languageLevel;

    if (!willReload() && DirectoryIndex.getInstance(myProject).isInitialized()) {
      JavaLanguageLevelPusher.pushLanguageLevel(myProject);
    }
  }

  public void reloadProjectOnLanguageLevelChange(final LanguageLevel languageLevel, final boolean forceReload) {
    if (willReload()) {
      myReloadProjectRequest = new Runnable() {
        public void run() {
          if (myProject.isDisposed()) return;
          if (myReloadProjectRequest != this) {
            // obsolete, another request has already replaced this one
            return;
          }
          if (!forceReload && myOriginalLanguageLevel.equals(getLanguageLevel())) {
            // the question does not make sense now
            return;
          }
          final String _message = ProjectBundle.message("project.language.level.reload.prompt", myProject.getName());
          if (Messages.showYesNoDialog(myProject, _message, ProjectBundle.message("project.language.level.reload.title"), Messages.getQuestionIcon()) == 0) {
            ProjectManager.getInstance().reloadProject(myProject);
          }
          myReloadProjectRequest = null;
        }
      };
      ApplicationManager.getApplication().invokeLater(myReloadProjectRequest, ModalityState.NON_MODAL);
    }
    else {
      // if the project is not open, reset the original level to the same value as mylanguageLevel has
      myOriginalLanguageLevel = languageLevel;
    }
  }

  private boolean willReload() {
    return myProject.isOpen() && !ApplicationManager.getApplication().isUnitTestMode();
  }

  public static class MyProjectExtension extends ProjectExtension {
    private final Project myProject;

    public MyProjectExtension(final Project project) {
      myProject = project;
    }

    public void readExternal(final Element element) throws InvalidDataException {
      ((LanguageLevelProjectExtensionImpl)getInstance(myProject)).readExternal(element);
    }

    public void writeExternal(final Element element) throws WriteExternalException {
      ((LanguageLevelProjectExtensionImpl)getInstance(myProject)).writeExternal(element);
    }
  }
}
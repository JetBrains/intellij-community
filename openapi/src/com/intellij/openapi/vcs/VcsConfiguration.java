/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.vcs;

import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.components.ProjectComponent;
import org.jdom.Element;

/**
 * author: lesya
 */

public final class VcsConfiguration implements JDOMExternalizable, ProjectComponent {

  public boolean PUT_FOCUS_INTO_COMMENT = false;
  public boolean SHOW_CHECKIN_OPTIONS = true;
  public boolean FORCE_NON_EMPTY_COMMENT = false;
  public String LAST_COMMIT_MESSAGE = "";
  public boolean SAVE_LAST_COMMIT_MESSAGE = true;
  public float CHECKIN_DIALOG_SPLITTER_PROPORTION = 0.8f;

  public boolean OPTIMIZE_IMPORTS_BEFORE_PROJECT_COMMIT = false;
  public boolean OPTIMIZE_IMPORTS_BEFORE_FILE_COMMIT = false;

  public boolean REFORMAT_BEFORE_PROJECT_COMMIT = false;
  public boolean REFORMAT_BEFORE_FILE_COMMIT = false;

  public float FILE_HISTORY_DIALOG_COMMENTS_SPLITTER_PROPORTION = 0.8f;
  public float FILE_HISTORY_DIALOG_SPLITTER_PROPORTION = 0.5f;

  public String ACTIVE_VCS_NAME;
  public boolean UPDATE_GROUP_BY_PACKAGES = false;
  public boolean SHOW_UPDATE_OPTIONS = true;
  public boolean SHOW_FILE_HISTORY_AS_TREE = false;
  public float FILE_HISTORY_SPLITTER_PROPORTION = 0.6f;
  public boolean SHOW_STATUS_OPTIONS = true;

  public static VcsConfiguration createEmptyConfiguration(Project project) {
    return new VcsConfiguration(project);
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }

  public static VcsConfiguration getInstance(Project project) {
    return project.getComponent(VcsConfiguration.class);
  }

  private final Project myProject;

  public VcsConfiguration(Project project) {
    myProject = project;
  }

  public void projectOpened() {

  }

  public void projectClosed() {

  }

  public String getComponentName() {
    return "VcsManagerConfiguration";
  }

  public void initComponent() {

  }

  public void disposeComponent() {

  }

  public String getConfiguredProjectVcs() {
    AbstractVcs vcs = ProjectLevelVcsManager.getInstance(myProject).findVcsByName(ACTIVE_VCS_NAME);
    if (vcs == null) {
      return "<none>";
    }
    else {
      return vcs.getDisplayName();
    }

  }
}

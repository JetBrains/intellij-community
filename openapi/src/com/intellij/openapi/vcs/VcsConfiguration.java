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

import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * author: lesya
 */

public final class VcsConfiguration implements JDOMExternalizable, ProjectComponent {

  public boolean PUT_FOCUS_INTO_COMMENT = false;
  public boolean SHOW_CHECKIN_OPTIONS = true;
  public boolean FORCE_NON_EMPTY_COMMENT = false;

  private ArrayList<String> myLastCommitMessages = new ArrayList<String>();

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
  private static final int MAX_STORED_MESSAGES = 10;
  private static final String MESSAGE_ELEMENT_NAME = "MESSAGE";

  public static VcsConfiguration createEmptyConfiguration(Project project) {
    return new VcsConfiguration(project);
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
    final List messages = element.getChildren(MESSAGE_ELEMENT_NAME);
    for (Iterator iterator = messages.iterator(); iterator.hasNext();) {
      saveCommitMessage(((Element)iterator.next()).getText());
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
    for (Iterator<String> iterator = myLastCommitMessages.iterator(); iterator.hasNext();) {
      String message = iterator.next();
      final Element messageElement = new Element(MESSAGE_ELEMENT_NAME);
      messageElement.setText(message);
      element.addContent(messageElement);
    }
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

  public void saveCommitMessage(final String comment) {
    if (comment.length() == 0) return;

    myLastCommitMessages.remove(comment);

    while (myLastCommitMessages.size() >= MAX_STORED_MESSAGES) {
      myLastCommitMessages.remove(0);
    }
    
    myLastCommitMessages.add(comment);
  }

  public String getLastCommitMessage() {
    if (myLastCommitMessages.isEmpty()) {
      return null;
    }
    else {
      return myLastCommitMessages.get(myLastCommitMessages.size() - 1);
    }
  }

  public ArrayList<String> getRecentMessages() {
    return new ArrayList<String>(myLastCommitMessages);
  }

  public void removeMessage(final String content) {
    myLastCommitMessages.remove(content);
  }
}

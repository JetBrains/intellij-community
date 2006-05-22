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
package com.intellij.openapi.vcs;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.List;

/**
 * author: lesya
 */

public final class VcsConfiguration implements JDOMExternalizable, ProjectComponent {
  @NonNls private static final String VALUE_ATTR = "value";

  public boolean OFFER_MOVE_TO_ANOTHER_CHANGELIST_ON_PARTIAL_COMMIT = true;
  public boolean CHECK_CODE_SMELLS_BEFORE_PROJECT_COMMIT = true;

  public enum StandardOption {
    CHECKIN(VcsBundle.message("vcs.command.name.checkin")),
    ADD(VcsBundle.message("vcs.command.name.add")),
    REMOVE(VcsBundle.message("vcs.command.name.remove")),
    EDIT(VcsBundle.message("vcs.command.name.edit")),
    CHECKOUT(VcsBundle.message("vcs.command.name.checkout")),
    STATUS(VcsBundle.message("vcs.command.name.status")),
    UPDATE(VcsBundle.message("vcs.command.name.update"));

    StandardOption(final String id) {
      myId = id;
    }

    private final String myId;

    public String getId() {
      return myId;
    }
  }

  public enum StandardConfirmation {
    ADD(VcsBundle.message("vcs.command.name.add")),
    REMOVE(VcsBundle.message("vcs.command.name.remove"));

    StandardConfirmation(final String id) {
      myId = id;
    }

    private final String myId;

    public String getId() {
      return myId;
    }
  }

  public boolean PUT_FOCUS_INTO_COMMENT = false;
  public boolean FORCE_NON_EMPTY_COMMENT = false;

  private ArrayList<String> myLastCommitMessages = new ArrayList<String>();
  public String LAST_COMMIT_MESSAGE = null;

  public boolean SAVE_LAST_COMMIT_MESSAGE = true;
  public float CHECKIN_DIALOG_SPLITTER_PROPORTION = 0.8f;

  public boolean OPTIMIZE_IMPORTS_BEFORE_PROJECT_COMMIT = false;

  public boolean REFORMAT_BEFORE_PROJECT_COMMIT = false;
  public boolean REFORMAT_BEFORE_FILE_COMMIT = false;

  public float FILE_HISTORY_DIALOG_COMMENTS_SPLITTER_PROPORTION = 0.8f;
  public float FILE_HISTORY_DIALOG_SPLITTER_PROPORTION = 0.5f;

  public boolean ERROR_OCCURED = false;

  public String ACTIVE_VCS_NAME;
  public boolean UPDATE_GROUP_BY_PACKAGES = false;
  public boolean SHOW_FILE_HISTORY_AS_TREE = false;
  public float FILE_HISTORY_SPLITTER_PROPORTION = 0.6f;
  private static final int MAX_STORED_MESSAGES = 10;
  @NonNls private static final String MESSAGE_ELEMENT_NAME = "MESSAGE";

  public static VcsConfiguration createEmptyConfiguration(Project project) {
    return new VcsConfiguration(project);
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
    final List messages = element.getChildren(MESSAGE_ELEMENT_NAME);
    for (final Object message : messages) {
      saveCommitMessage(((Element)message).getAttributeValue(VALUE_ATTR));
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
    for (String message : myLastCommitMessages) {
      final Element messageElement = new Element(MESSAGE_ELEMENT_NAME);
      messageElement.setAttribute(VALUE_ATTR, message);
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
      return VcsBundle.message("none.vcs.presentation");
    }
    else {
      return vcs.getDisplayName();
    }

  }

  public void saveCommitMessage(final String comment) {

    LAST_COMMIT_MESSAGE = comment;

    if (comment == null || comment.length() == 0) return;

    myLastCommitMessages.remove(comment);

    while (myLastCommitMessages.size() >= MAX_STORED_MESSAGES) {
      myLastCommitMessages.remove(0);
    }

    myLastCommitMessages.add(comment);
  }

  /**
   * @deprecated Use {@link #getLastNonEmptyCommitMessage()} instead.
   */
  public String getLastCommitMessage() {
    return getLastNonEmptyCommitMessage();
  }

  public String getLastNonEmptyCommitMessage() {
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

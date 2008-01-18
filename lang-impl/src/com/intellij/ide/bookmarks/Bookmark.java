package com.intellij.ide.bookmarks;

import com.intellij.openapi.project.Project;

import javax.swing.*;

abstract public class Bookmark {
  protected final Project myProject;
  private String myDescription;

  public Bookmark(Project project, String description) {
    myProject = project;
    myDescription = description;
  }

  abstract public void release();

  public Icon getIcon() {
    return null;
  }

  public String getDescription() {
    return myDescription;
  }

  public String getNotEmptyDescription() {
    return isDescriptionEmpty() ? null : myDescription;
  }

  public boolean isDescriptionEmpty() { return myDescription == null || myDescription.trim().length() == 0; }

  public void setDescription(String description) {
    myDescription = description;
  }

  public abstract boolean isValid();
}
/*
 * Copyright (c) 2000-05 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.openapi.project;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.InvalidDataException;
import org.jdom.JDOMException;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * Provides project management.
 */
public abstract class ProjectManager {
  /**
   * Gets <code>ProjectManager</code> instance.
   *
   * @return <code>ProjectManager</code> instance
   */
  public static ProjectManager getInstance() {
    return ApplicationManager.getApplication().getComponent(ProjectManager.class);
  }

  /**
   * Adds global listener to all projects
   *
   * @param listener listener to add
   */
  public abstract void addProjectManagerListener(ProjectManagerListener listener);

  /**
   * Removes global listener from all projects.
   *
   * @param listener listener to remove
   */
  public abstract void removeProjectManagerListener(ProjectManagerListener listener);

  /**
   * Adds listener to the specified project.
   *
   * @param project  project to add listener to
   * @param listener listener to add
   */
  public abstract void addProjectManagerListener(Project project, ProjectManagerListener listener);

  /**
   * Removes listener from the specified project.
   *
   * @param project  project to remove listener from
   * @param listener listener to remove
   */
  public abstract void removeProjectManagerListener(Project project, ProjectManagerListener listener);

  /**
   * Returns the list of currently opened projects.
   *
   * @return the array of currently opened projects.
   */
  public abstract Project[] getOpenProjects();

  /**
   * Returns the project which is used as a template for new projects. The template project
   * is always available, even when no other project is open.
   *
   * @return the template project instance.
   */

  public abstract Project getDefaultProject();

  /**
   * Loads and opens a project with the specified path. If the project file is from an older IDEA
   * version, prompts the user to convert it to the latest version. If the project file is from a
   * newer version, shows a message box telling the user that the load failed.
   *
   * @param filePath the .ipr file path
   * @return the opened project file, or null if the project failed to load because of version mismatch
   *         or because the project is already open.
   * @throws IOException          if the project file was not found or failed to read
   * @throws JDOMException        if the project file contained invalid XML
   * @throws InvalidDataException if the project file contained invalid data
   */
  @Nullable
  public abstract Project loadAndOpenProject(String filePath) throws IOException, JDOMException, InvalidDataException;

  /**
   * Closes the specified project.
   *
   * @param project the project to close.
   * @return true if the project was closed successfully, false if the closing was disallowed by the close listeners.
   */
  public abstract boolean closeProject(Project project);

  /**
   * Asynchronously reloads the specified project.
   *
   * @param project the project to reload.
   */
  public abstract void reloadProject(Project project);
}

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.localVcs;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FileStatusProvider;
import com.intellij.openapi.vcs.UpToDateRevisionProvider;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;

public abstract class LocalVcsServices{

  public static LocalVcsServices getInstance(Project project){
    return project.getComponent(LocalVcsServices.class);
  }

  public abstract UpToDateRevisionProvider getUpToDateRevisionProvider();
  public abstract FileStatusProvider getFileStatusProvider();
  public abstract CheckinEnvironment createCheckinEnvironment(AbstractVcs vcs);

}

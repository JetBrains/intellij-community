/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Jul 16, 2002
 * Time: 8:21:55 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.projectRoots;

import com.intellij.openapi.application.ApplicationManager;

import java.util.EventListener;

public abstract class ProjectJdkTable {
  public static ProjectJdkTable getInstance() {
    return ApplicationManager.getApplication().getComponent(ProjectJdkTable.class);
  }

  public abstract ProjectJdk findJdk(String name);

  public abstract ProjectJdk getInternalJdk();

  public abstract ProjectJdk[] getAllJdks();

  public abstract void addJdk(ProjectJdk jdk);

  public abstract void removeJdk(ProjectJdk jdk);

  public abstract void updateJdk(ProjectJdk originalJdk, ProjectJdk modifiedJdk);

  public static interface Listener extends EventListener {
    void jdkAdded(ProjectJdk jdk);

    void jdkRemoved(ProjectJdk jdk);

    void jdkNameChanged(ProjectJdk jdk, String previousName);
  }

  public abstract void addListener(Listener listener);

  public abstract void removeListener(Listener listener);
}

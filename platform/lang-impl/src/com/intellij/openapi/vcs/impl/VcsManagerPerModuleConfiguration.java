package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleComponent;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.startup.StartupManager;
import org.jdom.Element;

/**
 * @author mike
 */
public class VcsManagerPerModuleConfiguration implements JDOMExternalizable, ModuleComponent {
  private final Module myModule;
  public String ACTIVE_VCS_NAME = "";
  public boolean USE_PROJECT_VCS = true;

  public static VcsManagerPerModuleConfiguration getInstance(Module module) {
    return module.getComponent(VcsManagerPerModuleConfiguration.class);
  }

  public VcsManagerPerModuleConfiguration(final Module module) {
    myModule = module;
  }

  public void moduleAdded() {

  }

  public void projectClosed() {
  }

  public void projectOpened() {
  }

  public void disposeComponent() {
  }

  public void initComponent() { }

  public String getComponentName() {
    return "VcsManagerConfiguration";
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
    final ProjectLevelVcsManagerImpl vcsManager = (ProjectLevelVcsManagerImpl)ProjectLevelVcsManagerImpl.getInstanceEx(myModule.getProject());
    if (!USE_PROJECT_VCS) {
      final VirtualFile[] roots = ModuleRootManager.getInstance(myModule).getContentRoots();

      StartupManager.getInstance(myModule.getProject()).runWhenProjectIsInitialized(new Runnable() {
        public void run() {
          for(VirtualFile file: roots) {
            vcsManager.setDirectoryMapping(file.getPath(), ACTIVE_VCS_NAME);
          }
          vcsManager.cleanupMappings();
        }
      });
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    throw new WriteExternalException();
  }
}

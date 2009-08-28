package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.project.impl.ProjectStoreClassProvider;

/**
 * @author yole
 */
public class PlatformLangProjectStoreClassProvider implements ProjectStoreClassProvider {
  public Class<? extends IComponentStore> getProjectStoreClass(final boolean isDefaultProject) {
    return isDefaultProject ? DefaultProjectStoreImpl.class : ProjectWithModulesStoreImpl.class;
  }
}

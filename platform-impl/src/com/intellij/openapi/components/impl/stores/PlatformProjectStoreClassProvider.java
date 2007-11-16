package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.project.impl.ProjectStoreClassProvider;

/**
 * @author mike
 */
public class PlatformProjectStoreClassProvider implements ProjectStoreClassProvider {
  public Class<? extends IComponentStore> getProjectStoreClass(final boolean isDefaultProject) {
    return isDefaultProject ? DefaultProjectStoreImpl.class : ProjectStoreImpl.class;
  }
}

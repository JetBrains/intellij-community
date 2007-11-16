package com.intellij.openapi.project.impl;

import com.intellij.openapi.components.impl.stores.IComponentStore;

/**
 * @author mike
 */
public interface ProjectStoreClassProvider {
  Class<? extends IComponentStore> getProjectStoreClass(final boolean isDefaultProject);
}

package org.jetbrains.jps.incremental.resources;

import org.jetbrains.jps.model.module.JpsModule;

/**
 * @author Eugene Zhuravlev
 *         Date: 10/20/12
 */
public interface StandardResourceBuilderEnabler {
  boolean isResourceProcessingEnabled(JpsModule module);
}

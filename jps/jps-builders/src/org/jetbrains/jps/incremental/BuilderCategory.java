package org.jetbrains.jps.incremental;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/17/11
 */
public enum BuilderCategory {
  INITIAL,
  SOURCE_GENERATOR,
  SOURCE_INSTRUMENTER,
  SOURCE_PROCESSOR,
  TRANSLATOR,
  OVERWRITING_TRANSLATOR,
  RESOURCES_PROCESSOR,
  CLASS_INSTRUMENTER,
  CLASS_POST_PROCESSOR,
}

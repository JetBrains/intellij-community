package org.jetbrains.jps.incremental;

import com.intellij.openapi.util.Pair;

import java.util.Collection;
import java.util.EventListener;

/**
 * @author Eugene Zhuravlev
 *         Date: 5/21/12
 */
public interface BuildListener extends EventListener{

  /**
   * Note: when parallel build is on, might be called from several simultaneously running threads
   * @param paths collection of pairs [output root->relative path to generated file]
   */
  void filesGenerated(Collection<Pair<String, String>> paths);

  void filesDeleted(Collection<String> paths);
}

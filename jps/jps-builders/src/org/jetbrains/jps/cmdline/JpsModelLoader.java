package org.jetbrains.jps.cmdline;

import org.jetbrains.jps.Project;
import org.jetbrains.jps.model.JpsModel;

/**
 * @author nik
 */
public interface JpsModelLoader {
  JpsModel loadModel();

  Project loadOldProject();
}

package org.jetbrains.jps

/**
 * @author nik
 */
class Sdk extends Library {
  Sdk(project, name, initializer) {
    super(project, name, true, initializer);
  }
}

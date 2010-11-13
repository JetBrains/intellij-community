package org.jetbrains.jps.artifacts.ant

import org.jetbrains.jps.artifacts.Options

class CallAntBuildTask {
  private final Object ant;

  CallAntBuildTask(Object ant) {
    this.ant = ant;
  }

  void invokeAnt(Options options) {
    def String file = options.getAll()["file"].replace("file://", "");
    def String target = options.getAll()["target"];
    this.ant.ant([ 'antfile': file, 'target': target, 'dir': new File(file).getParent() ]);
  };
}

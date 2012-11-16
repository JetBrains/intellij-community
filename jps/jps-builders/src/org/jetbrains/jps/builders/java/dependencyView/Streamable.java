package org.jetbrains.jps.builders.java.dependencyView;

import java.io.PrintStream;

/**
 * Created with IntelliJ IDEA.
 * @author: db
 * Date: 24.04.12
 * Time: 0:44
 * To change this template use File | Settings | File Templates.
 */

interface Streamable {
  void toStream(DependencyContext context, PrintStream stream);
}

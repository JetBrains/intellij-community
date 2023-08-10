// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency;

import java.nio.file.Path;

/**
 * Represents a source (normally a text file) from which one or more nodes were produced.
 * One source can be associated with several Nodes and a Node can be produced basing on several sources
 */
public interface NodeSource extends SerializableGraphElement {
  Path getPath();

  boolean equals(Object other);

  int hashCode();
}

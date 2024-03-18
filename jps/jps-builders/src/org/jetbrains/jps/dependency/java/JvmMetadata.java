// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import org.jetbrains.jps.dependency.ExternalizableGraphElement;
import org.jetbrains.jps.dependency.diff.DiffCapable;
import org.jetbrains.jps.dependency.diff.Difference;

public interface JvmMetadata<T extends JvmMetadata<T, D>, D extends Difference> extends DiffCapable<T, D>, ExternalizableGraphElement {
}

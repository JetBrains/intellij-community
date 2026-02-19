// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.builders;

import com.dynatrace.hash4j.hashing.HashSink;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.jps.cmdline.ProjectDescriptor;

@ApiStatus.Experimental
@ApiStatus.Internal
public interface BuildTargetHashSupplier {
  void computeConfigurationDigest(ProjectDescriptor pd, HashSink hash);
}
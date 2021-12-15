// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.cache.model;

import org.jetbrains.jps.builders.BuildTargetType;

import java.util.Map;

public class ProjectBuildStatistic {
  private final Long myProjectRebuildTime;
  private final Map<BuildTargetType<?>, Long> myBuildTargetTypeStatistic;

  public ProjectBuildStatistic(Long time, Map<BuildTargetType<?>, Long> statistic) {
    myProjectRebuildTime = time;
    myBuildTargetTypeStatistic = statistic;
  }

  public Long getProjectRebuildTime() {
    return myProjectRebuildTime;
  }

  public Map<BuildTargetType<?>, Long> getBuildTargetTypeStatistic() {
    return myBuildTargetTypeStatistic;
  }
}

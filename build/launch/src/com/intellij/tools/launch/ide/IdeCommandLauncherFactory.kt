package com.intellij.tools.launch.ide

import com.intellij.tools.launch.PathsProvider
import com.intellij.tools.launch.environments.AbstractCommandLauncher

interface IdeCommandLauncherFactory<R> {
  fun create(localPaths: PathsProvider, classpathCollector: ClasspathCollector): Pair<AbstractCommandLauncher<R>, IdePathsInLaunchEnvironment>
}
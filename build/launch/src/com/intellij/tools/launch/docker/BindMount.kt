package com.intellij.tools.launch.docker

import com.intellij.tools.launch.environments.PathInLaunchEnvironment
import java.nio.file.Path

data class BindMount(val hostPath: Path, val containerPath: PathInLaunchEnvironment, val readonly: Boolean = false)
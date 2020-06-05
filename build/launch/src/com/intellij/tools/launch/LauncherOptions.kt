package com.intellij.tools.launch

interface LauncherOptions {
  val platformPrefix: String?
  val xmx: Int get() = 800
  val debugPort: Int get() = -1
  val debugSuspendOnStart: Boolean get() = false
  val javaArguments: List<String> get() = listOf()
  val ideaArguments: List<String> get() = listOf()
  val environment: Map<String, String> get() = mapOf()
  val beforeProcessStart: (ProcessBuilder) -> Unit get() = { }
}
package com.intellij.tools.launch.os

import java.io.File

// e.g. ~/.m2/ will be /mnt/cache/.m2 on TC
fun File.pathNotResolvingSymlinks(): String = this.absoluteFile.normalize().path
/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.intellij.build.images

import com.intellij.openapi.application.PathManager
import org.jetbrains.jps.model.serialization.JpsSerializationManager
import java.io.File

fun main(args: Array<String>) {
  val homePath = PathManager.getHomePath()
  val home = File(homePath)
  val project = JpsSerializationManager.getInstance().loadModel(homePath, null).project

  val util = project.modules.find { it.name == "util" } ?: throw IllegalStateException("Can't load module 'util'")

  val generator = IconsClassGenerator(home, util)
  project.modules.forEach { module ->
    generator.processModule(module)
  }
  generator.printStats()

  val optimizer = ImageSizeOptimizer(home)
  project.modules.forEach { module ->
    optimizer.optimizeIcons(module)
  }
  optimizer.printStats()

  val checker = ImageSanityChecker(home)
  project.modules.forEach { module ->
    checker.check(module)
  }
//  checker.printInfo()
  checker.printWarnings()

  println()
  println("Done")
}
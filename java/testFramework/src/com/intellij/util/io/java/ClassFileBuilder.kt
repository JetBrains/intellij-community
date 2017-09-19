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
package com.intellij.util.io.java

import com.intellij.util.io.DirectoryContentBuilder
import com.intellij.util.io.java.impl.ClassFileBuilderImpl
import org.jetbrains.jps.model.java.LanguageLevel
import kotlin.reflect.KClass

/**
 * Produces class-file with qualified name [name] in a place specified by [content]. If the class is not from the default package,
 * the produced file will be placed in a sub-directory according to its package.
 *
 * @author nik
 */
inline fun DirectoryContentBuilder.classFile(name: String, content: ClassFileBuilder.() -> Unit) {
  val builder = ClassFileBuilderImpl(name)
  builder.content()
  builder.generate(this)
}

abstract class ClassFileBuilder {
  var javaVersion: LanguageLevel = LanguageLevel.JDK_1_8
  var superclass: String = "java.lang.Object"
  var interfaces: List<String> = emptyList()
  var access: AccessModifier = AccessModifier.PUBLIC

  abstract fun field(name: String, type: KClass<*>, access: AccessModifier = AccessModifier.PRIVATE)

  /**
   * Adds a field which type is a class with qualified name [type]
   */
  abstract fun field(name: String, type: String, access: AccessModifier = AccessModifier.PRIVATE)
}

enum class AccessModifier { PRIVATE, PUBLIC, PROTECTED, PACKAGE_LOCAL }
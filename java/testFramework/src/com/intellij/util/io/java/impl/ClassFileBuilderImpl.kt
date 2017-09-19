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
package com.intellij.util.io.java.impl

import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.PathUtil
import com.intellij.util.io.DirectoryContentBuilder
import com.intellij.util.io.java.AccessModifier
import com.intellij.util.io.java.ClassFileBuilder
import org.jetbrains.jps.model.java.LanguageLevel
import org.jetbrains.org.objectweb.asm.ClassWriter
import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.org.objectweb.asm.Type
import kotlin.reflect.KClass

/**
 * @author nik
 */
class ClassFileBuilderImpl(private val name: String) : ClassFileBuilder() {
  private val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES)

  override fun field(name: String, type: String, access: AccessModifier) {
    addField(name, "L" + toJvmName(type) + ";", access)
  }

  override fun field(name: String, type: KClass<*>, access: AccessModifier) {
    addField(name, Type.getDescriptor(type.java), access)
  }

  private fun addField(name: String, typeDescriptor: String, access: AccessModifier) {
    writer.visitField(access.toAsmCode(), name, typeDescriptor, null, null).visitEnd()
  }

  fun generate(targetRoot: DirectoryContentBuilder) {
    writer.visit(javaVersion.toAsmCode(), access.toAsmCode(), toJvmName(name), null,
                 superclass.replace('.', '/'),
                 interfaces.map(::toJvmName).toTypedArray())
    writer.visitEnd()

    targetRoot.directories(StringUtil.getPackageName(name).replace('.', '/')) {
      file("${StringUtil.getShortName(name)}.class", writer.toByteArray())
    }
  }

  private fun DirectoryContentBuilder.directories(relativePath: String, content: DirectoryContentBuilder.() -> Unit) {
    if (relativePath.isEmpty()) {
      content()
    }
    else {
      directories(PathUtil.getParentPath(relativePath)) {
        dir(PathUtil.getFileName(relativePath)) {
          content()
        }
      }
    }
  }
}

private fun toJvmName(className: String) = className.replace('.', '/')

private fun LanguageLevel.toAsmCode() = when (this) {
  LanguageLevel.JDK_1_3 -> Opcodes.V1_3
  LanguageLevel.JDK_1_4 -> Opcodes.V1_4
  LanguageLevel.JDK_1_5 -> Opcodes.V1_5
  LanguageLevel.JDK_1_6 -> Opcodes.V1_6
  LanguageLevel.JDK_1_7 -> Opcodes.V1_7
  LanguageLevel.JDK_1_8 -> Opcodes.V1_8
  LanguageLevel.JDK_1_9 -> Opcodes.V1_9
  LanguageLevel.JDK_X -> throw UnsupportedOperationException("Java 10 isn't supported yet")
}

private fun AccessModifier.toAsmCode() = when (this) {
  AccessModifier.PROTECTED -> Opcodes.ACC_PROTECTED
  AccessModifier.PRIVATE -> Opcodes.ACC_PRIVATE
  AccessModifier.PUBLIC -> Opcodes.ACC_PUBLIC
  AccessModifier.PACKAGE_LOCAL -> 0
}
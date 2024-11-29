// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import org.jetbrains.org.objectweb.asm.*
import java.nio.file.Files
import java.nio.file.Path

//fun main() {
//  val file = Path.of(System.getProperty("user.home"),
//                     "Documents/idea/out/classes/production/intellij.platform.core/com/intellij/openapi/application/ApplicationNamesInfo.class")
//  val outFile = Path.of(System.getProperty("user.home"), "t/ApplicationNamesInfo.class")
//  injectAppInfo(file, outFile, "test")
//}

// see https://stackoverflow.com/a/49454118
internal fun injectAppInfo(inFileBytes: ByteArray, newFieldValue: String): ByteArray {
  val classReader = ClassReader(inFileBytes)
  val classWriter = ClassWriter(classReader, 0)
  classReader.accept(object : ClassVisitor(Opcodes.API_VERSION, classWriter) {
    override fun visitMethod(access: Int, name: String, desc: String, signature: String?, exceptions: Array<String?>?): MethodVisitor? {
      val methodVisitor = super.visitMethod(access, name, desc, signature, exceptions)
      if (name != "getAppInfoData") {
        return methodVisitor
      }

      return object : MethodVisitor(Opcodes.API_VERSION, methodVisitor) {
        override fun visitLdcInsn(value: Any) {
          if (value == "") {
            super.visitLdcInsn(newFieldValue)
          }
          else {
            super.visitLdcInsn(value)
          }
        }
      }
    }
  }, 0)
  return classWriter.toByteArray()
}
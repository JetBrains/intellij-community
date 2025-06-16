// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java.nodeBuilder

import org.jetbrains.bazel.jvm.util.emptyStringArray
import org.jetbrains.jps.dependency.java.KotlinMeta
import org.jetbrains.org.objectweb.asm.AnnotationVisitor
import org.jetbrains.org.objectweb.asm.Opcodes

internal class KotlinMetadataCrawler(
  private val resultConsumer: (KotlinMeta) -> Unit,
) : AnnotationVisitor(Opcodes.API_VERSION) {
  private var kind = -1
  private var version: IntArray? = null
  private var data1: Array<String>? = null
  private var data2: Array<String>? = null
  private var extraString: String? = null
  private var packageName: String? = null
  private var extraInt = 0

  override fun visit(name: String, value: Any) {
    @Suppress("UNCHECKED_CAST")
    when (name) {
      "k" -> kind = value as Int
      "mv" -> version = value as IntArray
      "d1" -> data1 = value as Array<String>
      "d2" -> data2 = value as Array<String>
      "xs" -> extraString = value as String
      "pn" -> packageName = value as String
      "xi" -> extraInt = value as Int
    }
  }

  override fun visitArray(name: String): AnnotationVisitor {
    return object : AnnotationVisitor(Opcodes.API_VERSION) {
      private val values = ArrayList<String>()

      override fun visit(name: String?, value: Any?) {
        if (value != null) {
          values.add(value as String)
        }
      }

      override fun visitEnd() {
        if (!values.isEmpty()) {
          val array = values.toArray(emptyStringArray)
          when (name) {
            "d1" -> data1 = array
            "d2" -> data2 = array
          }
        }
      }
    }
  }

  override fun visitEnd() {
    if (kind == 1) {
      return
    }

    resultConsumer(KotlinMeta(
      /* kind = */ kind,
      /* version = */ version,
      /* data1 = */ data1,
      /* data2 = */ data2,
      /* extraString = */ extraString,
      /* packageName = */ packageName,
      /* extraInt = */ extraInt,
    ))
  }
}
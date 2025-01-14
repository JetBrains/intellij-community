package org.jetbrains.bazel.jvm.abi

import org.jetbrains.org.objectweb.asm.AnnotationVisitor
import org.jetbrains.org.objectweb.asm.Opcodes

private const val KIND_FIELD_NAME = "k"
private const val METADATA_EXTRA_INT_FIELD_NAME = "xi"
private const val METADATA_VERSION_FIELD_NAME = "mv"
private const val METADATA_DATA_FIELD_NAME = "d1"
private const val METADATA_STRINGS_FIELD_NAME = "d2"
private const val METADATA_EXTRA_STRING_FIELD_NAME = "xs"
private const val METADATA_PACKAGE_NAME_FIELD_NAME = "pn"

internal class KotlinAnnotationVisitor(private val resultConsumer: (Metadata) -> Unit) : AnnotationVisitor(Opcodes.API_VERSION) {
  private var kind: Int = 1
  private var metadataVersion: IntArray = intArrayOf()
  private var data1: MutableList<String> = mutableListOf()
  private var data2: MutableList<String> = mutableListOf()
  private var extraString: String = ""
  private var packageName: String = ""
  private var extraInt: Int = 0

  override fun visit(name: String, value: Any?) {
    when (name) {
      KIND_FIELD_NAME -> kind = value as Int
      METADATA_EXTRA_INT_FIELD_NAME -> extraInt = value as Int
      METADATA_VERSION_FIELD_NAME -> metadataVersion = value as IntArray
      METADATA_EXTRA_STRING_FIELD_NAME -> extraString = value as String
      METADATA_PACKAGE_NAME_FIELD_NAME -> packageName = value as String
    }
  }

  override fun visitArray(name: String): AnnotationVisitor? {
    val destination = when (name) {
      METADATA_DATA_FIELD_NAME -> data1
      METADATA_STRINGS_FIELD_NAME -> data2
      else -> return null
    }
    return object : AnnotationVisitor(Opcodes.API_VERSION) {
      override fun visit(name: String?, value: Any?) {
        destination.add(value as String)
      }
    }
  }

  override fun visitEnd() {
    resultConsumer(Metadata(
      kind = kind,
      metadataVersion = metadataVersion,
      data1 = data1.toTypedArray(),
      data2 = data2.toTypedArray(),
      extraString = extraString,
      packageName = packageName,
      extraInt = extraInt,
    ))
  }
}

/**
 * Serialize a KotlinClassHeader to an existing Kotlin Metadata annotation visitor.
 */
internal fun visitKotlinMetadata(annotationVisitor: AnnotationVisitor, header: Metadata) {
  annotationVisitor.visit(KIND_FIELD_NAME, header.kind)
  annotationVisitor.visit(METADATA_VERSION_FIELD_NAME, header.metadataVersion)

  if (header.data1.isNotEmpty()) {
    val arrayVisitor = annotationVisitor.visitArray(METADATA_DATA_FIELD_NAME)
    for (v in header.data1) {
      arrayVisitor.visit(null, v)
    }
    arrayVisitor.visitEnd()
  }

  if (header.data2.isNotEmpty()) {
    val arrayVisitor = annotationVisitor.visitArray(METADATA_STRINGS_FIELD_NAME)
    for (v in header.data2) {
      arrayVisitor.visit(null, v)
    }
    arrayVisitor.visitEnd()
  }

  if (header.extraString.isNotEmpty()) {
    annotationVisitor.visit(METADATA_EXTRA_STRING_FIELD_NAME, header.extraString)
  }

  if (header.packageName.isNotEmpty()) {
    annotationVisitor.visit(METADATA_PACKAGE_NAME_FIELD_NAME, header.packageName)
  }

  if (header.extraInt != 0) {
    annotationVisitor.visit(METADATA_EXTRA_INT_FIELD_NAME, header.extraInt)
  }

  annotationVisitor.visitEnd()
}
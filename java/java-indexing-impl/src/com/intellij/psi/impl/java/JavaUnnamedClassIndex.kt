// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.psi.util.JavaUnnamedClassUtil
import com.intellij.util.indexing.*
import com.intellij.util.io.KeyDescriptor
import java.io.DataInput
import java.io.DataOutput

val id: ID<String, Void> = ID.create("java.unnamed.class")

class JavaUnnamedClassIndex: ScalarIndexExtension<String>() {
  private object UnnamedClassIndexer: DataIndexer<String, Void, FileContent> {
    override fun map(inputData: FileContent): MutableMap<String, Void?> {
      return when {
        JavaUnnamedClassUtil.isFileWithUnnamedClass(inputData.psiFile) -> {
          mutableMapOf(inputData.fileName to null)
        }
        else -> mutableMapOf()
      }
    }
  }

  override fun getName(): ID<String, Void> = id

  override fun getIndexer(): DataIndexer<String, Void, FileContent> = UnnamedClassIndexer

  override fun getKeyDescriptor(): KeyDescriptor<String> = object: KeyDescriptor<String> {
    override fun isEqual(val1: String?, val2: String?): Boolean = val1 == val2
    override fun getHashCode(value: String?): Int = value.hashCode()
    override fun save(out: DataOutput, value: String?) { if (value != null) { out.writeUTF(value) } }
    override fun read(`in`: DataInput): String = `in`.readUTF()
  }

  override fun getVersion(): Int = 0

  override fun getInputFilter(): FileBasedIndex.InputFilter = DefaultFileTypeSpecificInputFilter(JavaFileType.INSTANCE)

  override fun dependsOnFileContent(): Boolean = true
}
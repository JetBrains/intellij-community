// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative

import com.intellij.codeInsight.hints.declarative.impl.util.TinyTree
import com.intellij.testFramework.UsefulTestCase
import junit.framework.TestCase
import java.io.*
import com.intellij.codeInsight.hints.declarative.TinyTreeDebugNode as DebugNode

class TinyTreeTest : UsefulTestCase() {
  fun testAddToTreeInReverseOrder() {
    val tinyTree = createTree()
    assertTreeStructure(tinyTree)
  }

  private fun assertTreeStructure(tinyTree: TinyTree<String>) {
    assertEquals(6, tinyTree.size)
    val debugTree = DebugNode.buildDebugTree(tinyTree)
    val expectedNode = DebugNode(10, "root", mutableListOf(
      DebugNode(20, "left", mutableListOf()),
      DebugNode(30, "right", mutableListOf(
        DebugNode(40, "bottom left", mutableListOf()),
        DebugNode(45, "bottom middle", mutableListOf()),
        DebugNode(50, "bottom right", mutableListOf())
      ))
    ))
    TestCase.assertEquals(expectedNode, debugTree)
  }

  fun testReverseChildren() {
    val tinyTree = createTree()
    tinyTree.reverseChildren()
    val debugTree = DebugNode.buildDebugTree(tinyTree)
    val expectedNode = DebugNode(10, "root", mutableListOf(
      DebugNode(30, "right", mutableListOf(
        DebugNode(50, "bottom right", mutableListOf()),
        DebugNode(45, "bottom middle", mutableListOf()),
        DebugNode(40, "bottom left", mutableListOf())
      )),
      DebugNode(20, "left", mutableListOf())
    ))
    TestCase.assertEquals(expectedNode, debugTree)
  }

  fun testAddTooManyElements() {
    val tree = TinyTree(1, 1)
    assertThrows(TinyTree.TooManyElementsException::class.java) {
      repeat(200) {
        tree.add(0, it.toByte(), it)
      }
    }
  }

  fun testTreeSerde() {
    val externalizer = object : TinyTree.Externalizer<String>() {
      override fun writeDataPayload(output: DataOutput, payload: String): Unit = output.writeUTF(payload)
      override fun readDataPayload(input: DataInput): String = input.readUTF()
    }
    val baos = ByteArrayOutputStream()
    externalizer.save(DataOutputStream(baos), createTree())
    val deserializedTree = externalizer.read(DataInputStream(ByteArrayInputStream(baos.toByteArray())))
    assertTreeStructure(deserializedTree)
  }

  private fun createTree(): TinyTree<String> {
    //     10
    //   20   30
    //       40 50
    val tinyTree = TinyTree(10, "root")
    val rightIndex = tinyTree.add(0, 30, "right")
    tinyTree.add(0, 20, "left")
    tinyTree.add(rightIndex, 50, "bottom right")
    tinyTree.add(rightIndex, 45, "bottom middle")
    tinyTree.add(rightIndex, 40, "bottom left")
    return tinyTree
  }
}
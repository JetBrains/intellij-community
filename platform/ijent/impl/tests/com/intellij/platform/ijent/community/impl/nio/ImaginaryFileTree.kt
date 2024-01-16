// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.impl.nio

import com.intellij.platform.ijent.toDebugString

data class ImaginaryFileTree(
  val roots: MutableList<Node.Directory> = mutableListOf()
): Iterable<ImaginaryFileTree.Node.Directory> {
  sealed interface Node<N : Node<N>> {
    val name: String

    fun deepCopy(): N

    fun toPrettyString(): String

    // Beware that LinkedHashSet has incorrect 'equals'.
    data class Directory(override val name: String, val children: MutableList<Node<*>>) : Node<Directory>, Iterable<Node<*>> {
      override fun deepCopy(): Directory =
        copy(children = children.mapTo(mutableListOf()) { it.deepCopy() })

      override fun toPrettyString(): String =
        "dir(\"$name\") ${prettyChildren()}"

      override fun iterator(): Iterator<Node<*>> =
        children.iterator()
    }

    class RegularFile(override val name: String, var contents: ByteArray) : Node<RegularFile> {
      constructor(name: String, contents: String) : this(name, contents.toByteArray())

      override fun deepCopy(): RegularFile =
        RegularFile(name, contents.copyOf())

      override fun toPrettyString(): String =
        """
        |file("$name") {
        |  text("${contents.toDebugString()}")
        |}
        """.trimMargin("|")

      override fun toString(): String =
        "${javaClass.simpleName}(name = $name, contents = \"${contents.toDebugString()}\")"

      override fun equals(other: Any?): Boolean =
        other is RegularFile &&
        name == other.name &&
        contents.contentEquals(other.contents)

      override fun hashCode(): Int =
        name.hashCode() * 31 + contents.hashCode()
    }
  }

  fun deepCopy(): ImaginaryFileTree =
    ImaginaryFileTree(roots.mapTo(mutableListOf()) { it.deepCopy() })

  fun modify(body: RootBuilder.() -> Unit): ImaginaryFileTree = apply {
    RootBuilder(roots).body()
  }

  fun toPrettyString(): String =
    roots.joinToString("\n") { root ->
      "root(\"${root.name}\") ${root.prettyChildren()}"
    }

  override fun iterator(): Iterator<Node.Directory> =
    roots.iterator()

  @DslMarker
  annotation class FsContext

  @JvmInline
  @FsContext
  value class RootBuilder(val roots: MutableList<Node.Directory>) {
    fun root(name: String, body: DirectoryBuilder.() -> Unit) {
      val root =
        roots.find { it.name == name }
        ?: Node.Directory(name, mutableListOf()).also(roots::add)

      DirectoryBuilder(root).body()
    }
  }

  @JvmInline
  @FsContext
  value class DirectoryBuilder(val parent: Node.Directory) {
    fun dir(name: String, body: DirectoryBuilder.() -> Unit) {
      require('/' !in name && '\\' !in name) { "Directory name should not contain (back)slashes, but it contains: $name" }
      val directory: Node.Directory =
        parent.children
          .find { node -> node.name == name }
          ?.let { dir ->
            check(dir is Node.Directory) { "Not a directory: $dir" }
            dir
          }
        ?: Node.Directory(name, mutableListOf())
          .also(parent.children::add)

      DirectoryBuilder(directory).body()
    }

    fun file(name: String, body: FileBuilder.() -> Unit) {
      require('/' !in name && '\\' !in name) { "Regular file name should not contain (back)slashes, but it contains: $name" }
      val file: Node.RegularFile =
        parent.children
          .find { node -> node.name == name }
          ?.let { file ->
            check(file is Node.RegularFile) { "Not a regular file: $file" }
            file
          }
        ?: Node.RegularFile(name, byteArrayOf())
          .also(parent.children::add)

      FileBuilder(file).body()
    }
  }

  @JvmInline
  @FsContext
  value class FileBuilder(val file: Node.RegularFile) {
    fun text(contents: String) {
      file.contents = contents.toByteArray()
    }
  }

  companion object {
    private fun Node.Directory.prettyChildren(): String =
      if (children.isEmpty())
        "{}"
      else
        children.joinToString(separator = "\n", prefix = "{\n", postfix = "\n}") { child ->
          child.toPrettyString().lines().joinToString(separator = "\n") { line -> "  $line" }
        }
  }
}

fun ImaginaryFileTree.traverseDepthFirst(): Sequence<ImaginaryFileTree.Node<*>> {
  val deque = ArrayDeque<ImaginaryFileTree.Node<*>>(roots)
  return traverse({ deque.addAll(it.reversed()) }, deque::removeLastOrNull)
}

fun ImaginaryFileTree.traverseBreadthFirst(): Sequence<ImaginaryFileTree.Node<*>> {
  val deque = ArrayDeque<ImaginaryFileTree.Node<*>>(roots)
  return traverse(deque::addAll, deque::removeFirstOrNull)
}

private fun traverse(
  pushAll: (Iterable<ImaginaryFileTree.Node<*>>) -> Unit,
  pop: () -> ImaginaryFileTree.Node<*>?,
): Sequence<ImaginaryFileTree.Node<*>> = sequence {
  while (true) {
    val node = pop() ?: break
    yield(node)
    when (node) {
      is ImaginaryFileTree.Node.Directory -> {
        pushAll(node.children)
      }
      is ImaginaryFileTree.Node.RegularFile -> Unit
    }
  }
}
package org.jetbrains.jewel.foundation.tree

import java.io.File
import java.nio.file.Path

fun <T> buildTree(builder: TreeBuilder<T>.() -> Unit): Tree<T> =
    TreeBuilder<T>().apply(builder).build()

class TreeBuilder<T> {
    sealed class Element<T> {

        abstract val id: Any

        data class Leaf<T>(val data: T, override val id: Any) : Element<T>()
        data class Node<T>(
            val data: T,
            val isOpen: Boolean,
            override val id: Any,
            val childrenGenerator: ChildrenGeneratorScope<T>.() -> Unit
        ) : Element<T>()
    }

    private val heads = mutableListOf<Element<T>>()

    @Suppress("unused")
    fun addLeaf(data: T, id: Any = data.hashCode()) {
        heads.add(Element.Leaf(data, id))
    }

    fun addNode(
        data: T,
        isOpen: Boolean,
        id: Any = data.hashCode(),
        childrenGenerator: ChildrenGeneratorScope<T>.() -> Unit
    ) {
        heads.add(Element.Node(data, isOpen, id, childrenGenerator))
    }

    fun add(element: Element<T>) {
        heads.add(element)
    }

    fun build(): Tree<T> {
        val elements = mutableListOf<Tree.Element<T>>()
        for (index in heads.indices) {
            val previous: Tree.Element<T>? = elements.getOrNull(index - 1)?.let { evaluatePrevious(it) }
            val current = when (val elementBuilder = heads[index]) {
                is Element.Leaf -> Tree.Element.Leaf(
                    data = elementBuilder.data,
                    depth = 0,
                    childIndex = index,
                    parent = null,
                    previous = previous,
                    next = null,
                    id = elementBuilder.id
                )

                is Element.Node -> Tree.Element.Node(
                    data = elementBuilder.data,
                    depth = 0,
                    childIndex = index,
                    parent = null,
                    childrenGenerator = { parent -> generateElements(parent, elementBuilder) },
                    previous = previous,
                    next = null,
                    id = elementBuilder.id
                )
            }
            elements.add(current)
            previous?.also { it.next = current }
        }
        return Tree(elements)
    }
}

private fun <T> generateElements(
    parent: Tree.Element.Node<T>,
    parentElementBuilder: TreeBuilder.Element.Node<T>
): List<Tree.Element<T>> {
    val childrenGeneratorScope = ChildrenGeneratorScope(parent)
    parentElementBuilder.childrenGenerator(childrenGeneratorScope)
    val elements = mutableListOf<Tree.Element<T>>()
    for (index in childrenGeneratorScope.elements.indices) {
        val previous = if (index == 0) parent else elements[index - 1]
        val current = when (val elementBuilder = childrenGeneratorScope.elements[index]) {
            is TreeBuilder.Element.Leaf -> Tree.Element.Leaf(
                data = elementBuilder.data,
                depth = parent.depth + 1,
                childIndex = index,
                parent = parent,
                previous = previous,
                next = null,
                id = elementBuilder.id
            )

            is TreeBuilder.Element.Node -> Tree.Element.Node(
                data = elementBuilder.data,
                depth = parent.depth + 1,
                childIndex = index,
                parent = parent,
                childrenGenerator = { generateElements(it, elementBuilder) },
                previous = previous,
                next = null,
                id = elementBuilder.id
            )
        }
        previous.next = current
        elements.add(current)
    }
    return elements
}

private fun <T> evaluatePrevious(element: Tree.Element<T>): Tree.Element<T> = when (element) {
    is Tree.Element.Leaf -> element
    is Tree.Element.Node -> when (val nephews = element.children) {
        null -> element
        else -> if (nephews.isEmpty()) element else evaluatePrevious(nephews.last())
    }
}

class ChildrenGeneratorScope<T>(private val parentNode: Tree.Element.Node<T>) {

    data class ParentInfo<T>(val data: T, val depth: Int, val index: Int)

    val parent by lazy { ParentInfo(parentNode.data, parentNode.depth, parentNode.childIndex) }

    internal val elements = mutableListOf<TreeBuilder.Element<T>>()

    fun addLeaf(data: T, id: Any = data.hashCode()) {
        elements.add(TreeBuilder.Element.Leaf(data, id))
    }

    fun addNode(
        data: T,
        id: Any = data.hashCode(),
        isOpen: Boolean = false,
        childrenGenerator: ChildrenGeneratorScope<T>.() -> Unit = { }
    ) {
        elements.add(TreeBuilder.Element.Node(data, isOpen, id, childrenGenerator))
    }

    fun add(element: TreeBuilder.Element<T>) {
        elements.add(element)
    }
}

fun Path.asTree(isOpen: (File) -> Boolean = { false }) = toFile().asTree(isOpen)

fun File.asTree(isOpen: (File) -> Boolean = { false }) = buildTree {
    addNode(this@asTree, isOpen(this@asTree)) {
        generateFileNodes(isOpen)
    }
}

private fun ChildrenGeneratorScope<File>.generateFileNodes(isOpen: (File) -> Boolean) {
    val files = parent.data.listFiles() ?: return
    files.sortedBy { if (it.isDirectory) "a" else "b" }
        .forEach { file ->
            when {
                file.isFile -> addLeaf(file)
                else -> addNode(file, file.absolutePath, isOpen(file)) { generateFileNodes(isOpen) }
            }
        }
}

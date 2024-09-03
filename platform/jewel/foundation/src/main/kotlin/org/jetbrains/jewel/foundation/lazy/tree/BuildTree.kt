package org.jetbrains.jewel.foundation.lazy.tree

import java.io.File
import java.nio.file.Path
import org.jetbrains.jewel.foundation.GenerateDataFunctions

public fun <T> buildTree(builder: TreeBuilder<T>.() -> Unit): Tree<T> = TreeBuilder<T>().apply(builder).build()

public class TreeBuilder<T> : TreeGeneratorScope<T> {
    public sealed class Element<T> {
        public abstract val id: Any?

        @GenerateDataFunctions public class Leaf<T>(public val data: T, override val id: Any?) : Element<T>()

        @GenerateDataFunctions
        public class Node<T>(
            public val data: T,
            override val id: Any?,
            public val childrenGenerator: ChildrenGeneratorScope<T>.() -> Unit,
        ) : Element<T>()
    }

    private val heads = mutableListOf<Element<T>>()

    override fun addLeaf(data: T, id: Any?) {
        heads.add(Element.Leaf(data, id))
    }

    override fun addNode(data: T, id: Any?, childrenGenerator: ChildrenGeneratorScope<T>.() -> Unit) {
        heads.add(Element.Node(data, id, childrenGenerator))
    }

    override fun add(element: Element<T>) {
        heads.add(element)
    }

    public fun build(): Tree<T> {
        val elements = mutableListOf<Tree.Element<T>>()
        for (index in heads.indices) {
            val previous: Tree.Element<T>? = elements.getOrNull(index - 1)?.let { evaluatePrevious(it) }

            val current = getCurrentTreeElement(index, previous)
            elements.add(current)
            previous?.also { it.next = current }
        }
        return Tree(elements)
    }

    private fun getCurrentTreeElement(index: Int, previous: Tree.Element<T>?) =
        when (val elementBuilder = heads[index]) {
            is Element.Leaf ->
                Tree.Element.Leaf(
                    data = elementBuilder.data,
                    depth = 0,
                    childIndex = index,
                    parent = null,
                    previous = previous,
                    next = null,
                    id = elementBuilder.id ?: "$index",
                )

            is Element.Node ->
                Tree.Element.Node(
                    data = elementBuilder.data,
                    depth = 0,
                    childIndex = index,
                    parent = null,
                    childrenGenerator = { parent -> generateElements(parent, elementBuilder) },
                    previous = previous,
                    next = null,
                    id = elementBuilder.id ?: "$index",
                )
        }
}

private fun <T> generateElements(
    parent: Tree.Element.Node<T>,
    parentElementBuilder: TreeBuilder.Element.Node<T>,
): List<Tree.Element<T>> {
    val childrenGeneratorScope = ChildrenGeneratorScope(parent)
    parentElementBuilder.childrenGenerator(childrenGeneratorScope)
    val elements = mutableListOf<Tree.Element<T>>()
    for (index in childrenGeneratorScope.elements.indices) {
        val previous = if (index == 0) parent else elements[index - 1]
        val current =
            when (val elementBuilder = childrenGeneratorScope.elements[index]) {
                is TreeBuilder.Element.Leaf ->
                    Tree.Element.Leaf(
                        data = elementBuilder.data,
                        depth = parent.depth + 1,
                        childIndex = index,
                        parent = parent,
                        previous = previous,
                        next = null,
                        id = elementBuilder.id ?: (parent.id.toString() + "." + index),
                    )

                is TreeBuilder.Element.Node ->
                    Tree.Element.Node(
                        data = elementBuilder.data,
                        depth = parent.depth + 1,
                        childIndex = index,
                        parent = parent,
                        childrenGenerator = { generateElements(it, elementBuilder) },
                        previous = previous,
                        next = null,
                        id = elementBuilder.id ?: (parent.id.toString() + "." + index),
                    )
            }
        previous.next = current
        elements.add(current)
    }
    return elements
}

private fun <T> evaluatePrevious(element: Tree.Element<T>): Tree.Element<T> =
    when (element) {
        is Tree.Element.Leaf -> element
        is Tree.Element.Node ->
            when (val nephews = element.children) {
                null -> element
                else -> if (nephews.isEmpty()) element else evaluatePrevious(nephews.last())
            }
    }

public interface TreeGeneratorScope<T> {
    public fun addNode(data: T, id: Any? = null, childrenGenerator: ChildrenGeneratorScope<T>.() -> Unit = {})

    public fun addLeaf(data: T, id: Any? = null)

    public fun add(element: TreeBuilder.Element<T>)
}

public class ChildrenGeneratorScope<T>(private val parentElement: Tree.Element.Node<T>) : TreeGeneratorScope<T> {
    @GenerateDataFunctions public class ParentInfo<T>(public val data: T, public val depth: Int, public val index: Int)

    public val parent: ParentInfo<T> by lazy {
        ParentInfo(parentElement.data, parentElement.depth, parentElement.childIndex)
    }

    internal val elements = mutableListOf<TreeBuilder.Element<T>>()

    override fun addLeaf(data: T, id: Any?) {
        elements.add(TreeBuilder.Element.Leaf(data, id))
    }

    override fun addNode(data: T, id: Any?, childrenGenerator: ChildrenGeneratorScope<T>.() -> Unit) {
        elements.add(TreeBuilder.Element.Node(data, id, childrenGenerator))
    }

    override fun add(element: TreeBuilder.Element<T>) {
        elements.add(element)
    }
}

public fun Path.asTree(isOpen: (File) -> Boolean = { false }): Tree<File> = toFile().asTree(isOpen)

public fun File.asTree(isOpen: (File) -> Boolean = { false }): Tree<File> = buildTree {
    addNode(this@asTree, isOpen(this@asTree)) { generateFileNodes(isOpen) }
}

private fun ChildrenGeneratorScope<File>.generateFileNodes(isOpen: (File) -> Boolean) {
    val files = parent.data.listFiles() ?: return
    files
        .sortedBy { if (it.isDirectory) "a" else "b" }
        .forEach { file ->
            when {
                file.isFile -> addLeaf(file, file.absolutePath)
                else -> addNode(file, file.absolutePath) { generateFileNodes(isOpen) }
            }
        }
}

package org.jetbrains.jewel.foundation.lazy.tree

import org.jetbrains.jewel.foundation.lazy.tree.Tree.Element.Node

@Suppress("UNCHECKED_CAST") public fun <T> emptyTree(): Tree<T> = Tree.EMPTY as Tree<T>

public class Tree<T> internal constructor(public val roots: List<Element<T>>) {
    public companion object {
        internal val EMPTY = Tree(roots = emptyList<Element<Any?>>())
    }

    public fun isEmpty(): Boolean = roots.isEmpty()

    private fun walk(breathFirst: Boolean) = sequence {
        val queue = roots.toMutableList()
        while (queue.isNotEmpty()) {
            val next = queue.removeFirst()
            yield(next)
            if (next is Node) {
                next.open()
                if (breathFirst) {
                    queue.addAll(next.children.orEmpty())
                } else {
                    queue.addAll(0, next.children.orEmpty())
                }
            }
        }
    }

    public fun walkBreadthFirst(): Sequence<Element<T>> = walk(true)

    public fun walkDepthFirst(): Sequence<Element<T>> = walk(false)

    public sealed interface Element<T> {
        public val data: T
        public val depth: Int
        public val parent: Element<T>?
        public val childIndex: Int
        public var next: Element<T>?
        public var previous: Element<T>?
        public val id: Any

        public fun path(): List<Element<T>> =
            buildList {
                    var next: Element<T>? = this@Element
                    while (next != null) {
                        add(next)
                        next = next.parent
                    }
                }
                .reversed()

        public fun previousElementsIterable(): Iterable<Element<T>> = Iterable {
            elementIterator(previous) { it.previous }
        }

        public fun nextElementsIterable(): Iterable<Element<T>> = Iterable { elementIterator(next) { it.next } }

        public class Leaf<T>(
            override val data: T,
            override val depth: Int,
            override val childIndex: Int,
            override val parent: Element<T>?,
            override var previous: Element<T>?,
            override var next: Element<T>?,
            override val id: Any,
        ) : Element<T>

        public class Node<T>(
            override val data: T,
            override val depth: Int,
            override val childIndex: Int,
            override val parent: Element<T>?,
            private val childrenGenerator: (parent: Node<T>) -> List<Element<T>>,
            override var next: Element<T>?,
            override var previous: Element<T>?,
            override val id: Any,
        ) : Element<T> {
            public var children: List<Element<T>>? = null
                private set

            private fun evaluateChildren() {
                children = childrenGenerator(this)
            }

            private fun connectChildren() {
                val children = children ?: return
                if (children.isNotEmpty()) {
                    next?.also {
                        it.previous = children.last()
                        children.last().next = it
                    }
                    next = children.first()
                    children.first().previous = this
                }
            }

            private fun detachChildren() {
                val children = children ?: return
                if (children.isNotEmpty()) {
                    next = children.last().next
                    next?.previous = this
                }
            }

            public fun open(reloadChildren: Boolean = false) {
                if (reloadChildren || children == null) evaluateChildren()
                connectChildren()
            }

            public fun close() {
                detachChildren()
                children?.asSequence()?.filterIsInstance<Node<*>>()?.forEach { it.closeRecursively() }
            }

            private fun closeRecursively() {
                children?.forEach { if (it is Node) it.closeRecursively() }
            }
        }
    }
}

private fun <T> elementIterator(initial: Tree.Element<T>?, next: (Tree.Element<T>) -> Tree.Element<T>?) = iterator {
    var current = initial ?: return@iterator
    yield(current)
    while (true) {
        current = next(current) ?: break
        yield(current)
    }
}

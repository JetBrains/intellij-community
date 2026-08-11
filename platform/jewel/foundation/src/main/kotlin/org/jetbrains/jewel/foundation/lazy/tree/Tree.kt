package org.jetbrains.jewel.foundation.lazy.tree

import org.jetbrains.jewel.foundation.lazy.tree.Tree.Element.Node

/** Returns a shared empty [Tree] instance with no root elements. */
@Suppress("UNCHECKED_CAST") public fun <T> emptyTree(): Tree<T> = Tree.EMPTY as Tree<T>

/**
 * A lazy tree data structure whose top-level entries are stored as a flat list of [roots]. Child nodes are generated on
 * demand when a [Element.Node] is opened.
 */
public class Tree<T> internal constructor(public val roots: List<Element<T>>) {
    /** Provides the [EMPTY] singleton for an empty tree with no root elements. */
    public companion object {
        internal val EMPTY = Tree(roots = emptyList<Element<Any?>>())
    }

    /** Returns `true` if this tree has no root elements. */
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

    /** Returns a [Sequence] that traverses the tree in breadth-first order, opening nodes as it visits them. */
    public fun walkBreadthFirst(): Sequence<Element<T>> = walk(true)

    /** Returns a [Sequence] that traverses the tree in depth-first order, opening nodes as it visits them. */
    public fun walkDepthFirst(): Sequence<Element<T>> = walk(false)

    /**
     * An element in the tree, holding [data] plus [depth], [childIndex], a [parent] pointer, and [next]/[previous]
     * pointers into the tree's flattened linked list.
     */
    public sealed interface Element<T> {
        /** The data payload held by this element. */
        public val data: T

        /** The nesting depth of this element, where 0 means a root element. */
        public val depth: Int

        /** The parent element of this element, or `null` if it is a root element. */
        public val parent: Element<T>?

        /** The zero-based index of this element among its siblings. */
        public val childIndex: Int

        /** The next element in the flattened linked list, or `null` if this is the last element. */
        public var next: Element<T>?

        /** The previous element in the flattened linked list, or `null` if this is the first element. */
        public var previous: Element<T>?

        /** A stable identifier for this element, used to distinguish it from other elements in the tree. */
        public val id: Any

        /** Returns the ordered path from the root of the tree down to this element (inclusive). */
        public fun path(): List<Element<T>> =
            buildList {
                    var next: Element<T>? = this@Element
                    while (next != null) {
                        add(next)
                        next = next.parent
                    }
                }
                .reversed()

        /** Returns an [Iterable] over elements preceding this one in the flattened linked list, in reverse order. */
        public fun previousElementsIterable(): Iterable<Element<T>> = Iterable {
            elementIterator(previous) { it.previous }
        }

        /** Returns an [Iterable] over elements following this one in the flattened linked list, in forward order. */
        public fun nextElementsIterable(): Iterable<Element<T>> = Iterable { elementIterator(next) { it.next } }

        /** A terminal element in the tree that holds [data] and has no children. */
        public class Leaf<T>(
            override val data: T,
            override val depth: Int,
            override val childIndex: Int,
            override val parent: Element<T>?,
            override var previous: Element<T>?,
            override var next: Element<T>?,
            override val id: Any,
        ) : Element<T>

        /**
         * A branching element in the tree that holds [data] and can lazily generate [children] when [open] is called.
         */
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
            /** The lazily evaluated children of this node, or `null` if [open] has not been called yet. */
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

            /**
             * Evaluates and links this node's children into the flat element linked list. If [reloadChildren] is
             * `true`, the children generator is invoked again even if children were already evaluated.
             *
             * @param reloadChildren Whether to force re-evaluation of the children generator.
             */
            public fun open(reloadChildren: Boolean = false) {
                if (reloadChildren || children == null) evaluateChildren()
                connectChildren()
            }

            /**
             * Detaches this node's children from the flat element linked list and recursively closes all descendant
             * nodes.
             */
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

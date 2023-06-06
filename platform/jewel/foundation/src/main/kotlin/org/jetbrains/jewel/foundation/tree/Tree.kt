package org.jetbrains.jewel.foundation.tree

class Tree<T> internal constructor(internal val roots: List<Element<T>>) : Iterable<Tree.Element<T>> {

    override fun iterator(): Iterator<Element<T>> = elementIterator(roots.firstOrNull()) { it.next }

    sealed interface Element<T> {

        val data: T
        val depth: Int
        val parent: Element<T>?
        val childIndex: Int
        var next: Element<T>?
        var previous: Element<T>?
        val id: Any

        fun path() = buildList {
            var next: Element<T>? = this@Element
            while (next != null) {
                add(next)
                next = next.parent
            }
        }.reversed()

        fun idPath() = path().map { it.id }

        fun previousElementsIterable() = Iterable { elementIterator(previous) { it.previous } }
        fun nextElementsIterable() = Iterable { elementIterator(next) { it.next } }

        class Leaf<T>(
            override val data: T,
            override val depth: Int,
            override val childIndex: Int,
            override val parent: Element<T>?,
            override var previous: Element<T>?,
            override var next: Element<T>?,
            override val id: Any
        ) : Element<T>

        class Node<T>(
            override val data: T,
            override val depth: Int,
            override val childIndex: Int,
            override val parent: Element<T>?,
            private val childrenGenerator: (parent: Node<T>) -> List<Element<T>>,
            override var next: Element<T>?,
            override var previous: Element<T>?,
            override val id: Any
        ) : Element<T> {

            var children: List<Element<T>>? = null
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

            fun open(reloadChildren: Boolean = false) {
                if (reloadChildren || children == null) evaluateChildren()
                connectChildren()
            }

            fun close() {
                detachChildren()
                children?.asSequence()
                    ?.filterIsInstance<Node<*>>()
                    ?.forEach { it.closeRecursively() }
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

/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ui.noria

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import java.util.*
import java.util.function.Supplier

/**
 * Noria is a library for building declarative and reactive UI for abstract Toolkit.
 *
 * The idea behind it is to build UI using composition of pure functions of data (render-function).
 * Data is a pair of (Props, Children).
 * Props is an effectively immutable representation of component's properties and
 * Children is a list of other Elements that could eventually be added as children somewhere downstream.
 *
 * There are two kinds of UI components:
 *
 * Primitive ones should be considered to be built-in.
 * They correspond to actual Toolkit's components and have no render-function.
 * They are provided by implementations of PrimitiveComponentType extension point.
 *
 * Here is how we declare a primitive component:
 *
 *
 * val panel = primitiveComponent<PanelProps>("panel")
 *
 * User ones are user-defined components. *
 * The framework will denote User component to another component by calling corresponding render-function or will supply actual Toolkit's Node.
 * Render-function returns an Element. Element is a data-representation of any component (User or Primitive) which is not reified yet.
 *
 * User-defined components should be declared in the following fashion:
 *
 *
 * val myComponent = component<ComponentProps>("some string representing it's type") { props: ComponentProps, children: List<Element> ->
 *   panel {
 *      props = PanelProps(...)
 *      checkbox {
 *        props = CheckboxProps(...)
 *      }
 *      for (c in children) {
 *        child(c)
 *      }
 *   }
 * }
 *
 * This code builds a component which denotes to panel with specified props and children of some checkbox and other children passed to it as parameter.
 *
 * For user's convenience render function is invoked on a receiver of type ElementBuilder.
 * It's purpose is to collect all information about Element needed to build it. props setter sets props, child() methods adds a child.
 *
 * User-components are re-rendered once they receive different (Props, Children) or state they are looking at changes.
 * There are special state container called Cell<T>.
 * Reads from it are tracked by the framework and if mutation of it happens then component it reads from is a subject to update.
 * Cell should store immutable value, because change-detection is performed by calling equals with old and new Cell values.
 *
 * There are two kinds of Cells:
 *
 * VarCell stores mutable value and should be treated as model. One can update it's value directly. (e.g. in button's callback function)
 * Cell type has no setter and used to represent a derivation of other cells.
 *
 * This will construct mutable cell, which can be updated:
 *
 * val model = cell(5)
 * model.value++
 *
 * And this will build a derived cell which is updated in reactive fashion once any of cells it depends on get changed:
 *
 * val derived = cell {a.value + b.value}
 *
 * Please note that derived cells are lazy. It's body is not evaluated unless nobody is reading from it.
 * 'derived.value' written in render function or other 'cell {}' block will be tracked and re-evaluated when a or b will change it's value.
 * Corresponding components will get updated.
 *
 * There are one more function exists to perform side-effects once cell change:
 * track(Disposable) {
 *   side-effects here
 * }
 *
 * Side effects in track() should be idempotent since it's hard to predict how many times the block will get executed.
 * It is guaranteed to be invoked when any of cells it reads from is changed. The block is executed immediately for the first time when track() is called.
 * Disposable is passed to track lifetime of this subscription.
 */

private val NoRender = { throw UnsupportedOperationException() }

class ElementType(val type: Any,
                  internal val renderFn: () -> (Any, List<Element>) -> Element = NoRender) {
  override fun equals(other: Any?): Boolean {
    return other is ElementType && other.type == type
  }

  override fun hashCode(): Int = type.hashCode() + 1
}

data class Element(val key: Any?, val type: ElementType, val props: Any, val children: List<Element>, val index: Int)

interface Toolkit<Node> {
  fun isPrimitive(e: ElementType): Boolean
  fun createNode(e: Element): Node
  fun performUpdates(l: List<Update<Node>>, root: Node)
  fun scheduleReconcile(function: () -> Unit) {
    function()
  }
}

private interface Component<Node> : Disposable {
  val node: Node
  val parentNode: Node
  val element: Element
  fun reconcile(e: Element, tk: Toolkit<Node>, update: () -> Unit) : Pair<Component<Node>, List<Update<Node>>>
}

private fun<T> longestCommonSubsequence(a: List<T>, b: List<T>) : List<T> {
  val m = a.size
  val n = b.size
  val dp = Array(m + 1) { IntArray(n + 1) }
  for (i in dp.indices) {
    for (j in dp[i].indices) {
      if (i == 0 || j == 0) {
        dp[i][j] = 0
      }
      else if (a[i - 1] == b[j - 1]) {
        dp[i][j] = 1 + dp[i - 1][j - 1]
      }
      else {
        dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1])
      }
    }
  }

  fun backtrack(i: Int, j: Int): List<T> =
      if (i == 0 || j == 0) emptyList()
      else if (a[i-1] == b[j-1]) backtrack(i - 1, j - 1) + a[i-1]
      else if (dp[i][j - 1] > dp[i - 1][j]) backtrack(i, j - 1)
      else backtrack(i - 1, j)

  return backtrack(m , n )
}

private fun<Node> buildComponent(parent: Node, e: Element, r: Toolkit<Node>, update: () -> Unit): Pair<Component<Node>, List<Update<Node>>> =
  if (r.isPrimitive(e.type)) {
    val newNode = r.createNode(e)
    val childComponents = e.children.map { element -> buildComponent(newNode, element, r, update) }
    PrimitiveComponent(node = newNode,
                       parentNode = parent,
                       element = e,
                       children = childComponents.map { it.first }) to
        childComponents.flatMap { it.second } + AddChild(e.props, e.index, newNode, parent)
  } else {
    val context = ReactiveContext(onInvalidate = update)
    val render = e.type.renderFn()
    val substElement = withReactiveContext(context, { render(e.props, e.children) }).copy(index = e.index)
    val (c, updates) = buildComponent(parent, substElement, r, update)
    UserComponent(node = c.node,
                  render = render,
                  element = e,
                  parentNode = parent,
                  substitution = c,
                  reactiveContext = context) to updates
  }

private fun<Node> reconcileChildren(c: PrimitiveComponent<Node>,
                            e: Element,
                            tk: Toolkit<Node>,
                            update: () -> Unit) : Pair<List<Component<Node>>, List<Update<Node>>> {
  val oldKeys = c.children.map { it.element.key }
  val newKeys = e.children.map { it.key }
  return if (oldKeys != newKeys) {
    val oldComponentsForKeys = c.children.map { it.element.key to it }.toMap()
    val common = longestCommonSubsequence(oldKeys, newKeys).toSet()
    val reconciliation =
        e.children.map { e ->
          val old = oldComponentsForKeys[e.key]
          if (old != null) {
            val (new, updates) = old.reconcile(e, tk, update)
            if (!common.contains(e.key)) {
              new to updates + AddChild(childProps = e.props, index = e.index, child = new.node, parent = c.node)
            }
            else {
              new to updates
            }
          }
          else {
            buildComponent(c.node, e, tk, update)
          }
        }
    val newKeySet = newKeys.toSet()
    val updates = c.children
        .filter { !common.contains(it.element.key) }
        .flatMap { c -> listOf(RemoveChild(c.node, c.parentNode)).let {
            if (!newKeySet.contains(c.element.key)) {
              c.dispose()
              it + DestroyNode(c.node, c.element.type)
            } else it
          }
        } + reconciliation.flatMap { it.second }
    reconciliation.map { it.first } to updates
  }
  else {
    val reconciliation = c.children.zip(e.children).map { it.first.reconcile(it.second, tk, update) }
    reconciliation.map { it.first } to reconciliation.flatMap { it.second }
  }
}

private data class PrimitiveComponent<Node>(override val node: Node,
                                    override val element : Element,
                                    override val parentNode: Node,
                                    val children: List<Component<Node>>) : Component<Node> {
  override fun dispose() {
    children.forEach { it.dispose() }
  }

  override fun reconcile(e: Element, tk: Toolkit<Node>, update: () -> Unit) : Pair<Component<Node>, List<Update<Node>>> =
    when {
      e.type != element.type -> {
        val (newC, updates) = buildComponent(parentNode, e, tk, update)
        newC to updates + RemoveChild(child = node, parent = parentNode) + DestroyNode(node, element.type)
      }
      else -> {
        val updates =
            if (e.props != element.props)
              listOf(UpdateProps(element.props, e.props, e.type, node))
            else
              emptyList()
        val result = if (element != e) copy(element = e) else this
        val (childrenReconciled, childrenUpdates) = reconcileChildren(result, e, tk, update)
        if (childrenReconciled != result.children) {
          result.copy (children = childrenReconciled) to updates + childrenUpdates
        } else {
          result to updates + childrenUpdates
        }
      }
    }
}

private data class UserComponent<Node>(override val node: Node,
                               override val element : Element,
                               override val parentNode: Node,
                               val render: (Any, List<Element>) -> Element,
                               val substitution: Component<Node>,
                               val reactiveContext: ReactiveContext) : Component<Node> {
  override fun dispose() {
    reactiveContext.invalidate()
    substitution.dispose()
  }

  override fun reconcile(e: Element, tk: Toolkit<Node>, update: () -> Unit) : Pair<Component<Node>, List<Update<Node>>> =
    when {
      e.type != element.type -> {
        val context = ReactiveContext(onInvalidate = update)
        val render = e.type.renderFn()
        val substElement = withReactiveContext(context) { render(e.props, e.children) }
        val (subst, updates) = buildComponent(parentNode, substElement, tk, update)
        substitution.dispose()
        copy(element = e,
             render = render,
             reactiveContext = context,
             substitution = subst,
             node = subst.node) to updates + RemoveChild(parentNode, node) + DestroyNode(node, element.type)
      }
      else -> {
        val (substElement, context) =
            if (reactiveContext.dirty || e.props != element.props || e.children != element.children) {
              val context = ReactiveContext(onInvalidate = update)
              withReactiveContext(context) {
                render(e.props, e.children)
              } to context
            }
            else
              substitution.element to reactiveContext

        val (subst, updates) = substitution.reconcile(substElement.copy(index = e.index), tk, update)
        (if (subst != substitution || element != e)
          copy(element = e,
               reactiveContext = context,
               substitution = subst,
               node = subst.node)
        else this) to updates
      }
    }
}

@Suppress("unused")
interface Update<Node>

data class UpdateProps<Node> (val oldProps: Any,
                              val newProps: Any,
                              val type: ElementType,
                              val node: Node) : Update<Node>

data class AddChild<Node> (val childProps: Any,
                           val index: Int,
                           val child: Node,
                           val parent: Node) : Update<Node>

data class RemoveChild<Node>(val child: Node,
                             val parent: Node) : Update<Node>

data class DestroyNode<Node>(val node: Node, val type: ElementType) : Update<Node>

interface NoriaHandle<Node> {
  fun getPreferredFocusedNode() : Node?
}

interface Focusable {
  var autoFocus: Boolean
}

private fun <Node> firstDescendant(c: Component<Node>, pred: (Element) -> Boolean): Component<Node>? {
  if (pred(c.element)) {
    return c
  }
  return when (c) {
    is UserComponent<Node> -> firstDescendant(c.substitution, pred)
    is PrimitiveComponent<Node> -> c.children.map { firstDescendant(it, pred) }.filterNotNull().firstOrNull()
    else -> throw IllegalStateException()
  }
}

fun<Node> mount(parentDisposable: Disposable,
                element: Element,
                root: Node,
                toolkit: Toolkit<Node>): NoriaHandle<Node> {
  var rootC: Component<Node>? = null
  fun reconciler() {
    toolkit.scheduleReconcile {
      val (c, updates) = rootC!!.reconcile(element, toolkit, ::reconciler)
      rootC = c
      toolkit.performUpdates(updates, root)
    }
  }
  val reconciliation = buildComponent(root, element, toolkit, ::reconciler)
  rootC = reconciliation.first
  val updates = reconciliation.second
  toolkit.performUpdates(updates, root)

  Disposer.register(parentDisposable, Disposable {
    toolkit.scheduleReconcile {
      toolkit.performUpdates(listOf(RemoveChild(parent = root, child = rootC!!.node)), root)
    }
  })
  return object: NoriaHandle<Node> {
    override fun getPreferredFocusedNode(): Node? = firstDescendant(rootC!!, {it.props is Focusable && it.props.autoFocus})?.node
  }
}

internal class ReactiveContext(val dependencies: MutableSet<ReactiveContext> = mutableSetOf(),
                      val dependants: MutableSet<ReactiveContext> = mutableSetOf(),
                      var dirty: Boolean = false,
                      val onInvalidate: () -> Unit = {}) {
  fun invalidate() {
    if (!dirty) {
      dirty = true
      val dependantsCopy = dependants.toList()
      val dependenciesCopy = dependencies.toList()
      dependencies.clear()
      dependenciesCopy.forEach {
        it.dependants.remove(this)
      }
      dependantsCopy.forEach { it.invalidate() }
      onInvalidate()
    }
  }
}

internal val reactiveContext: ThreadLocal<ReactiveContext> = ThreadLocal.withInitial { ReactiveContext() }

internal fun<T> withReactiveContext(c: ReactiveContext, f: () -> T) : T {
  val old = reactiveContext.get()
  reactiveContext.set(c)
  try {
    val t = f()
    return t
  }
  finally {
    reactiveContext.set(old)
  }
}

interface Cell<out T> {
  val value: T
}

interface VarCell<T>: Cell<T> {
  override var value: T
}

internal abstract class CellBase {
  abstract val context: ReactiveContext
}

internal class DerivedCell<out T : Any?>(val f: () -> T) : CellBase(), Cell<T> {
  private var cache: T? = null
  override var context: ReactiveContext = ReactiveContext(dirty = true)

  override val value: T
    get() {
      val current = reactiveContext.get()
      current.dependencies.add(context)

      if (context.dirty) {
        cache = withReactiveContext(context, f)
        context.dirty = false
      }
      context.dependants.add(current)
      return cache as T
    }
}

internal class VarCellImpl<T>(initial: T) : CellBase(), VarCell<T> {
  private var _value: T = initial
  override val context = ReactiveContext()
  override var value: T
    get() {
      val current = reactiveContext.get()
      current.dependencies.add(context)
      context.dependants.add(current)
      return _value
    }
    set(value) {
      if (value != _value) {
        _value = value
        val dependants = ArrayList(context.dependants)
        dependants.forEach { it.invalidate() }
      }
    }
}

fun <T : Any?> cell(f: () -> T): Cell<T> = DerivedCell(f)
fun <T : Any?> derivedCell(f: Supplier<T>): Cell<T> = DerivedCell { f.get() }
fun <T : Any?> cell(t: T): VarCell<T> = VarCellImpl(t)

fun track(d: Disposable, f: () -> Unit): Unit {
  val derivedCell = DerivedCell(f)
  var disposed = false
  derivedCell.context = ReactiveContext(dirty = true,
                                        onInvalidate = {
                                          if (!disposed) {
                                            derivedCell.value
                                          }})
  Disposer.register(d, Disposable {
    disposed = true
    derivedCell.context.invalidate()
  })
  derivedCell.value
}

class ElementBuilder<T: Any>(val parent: ElementBuilder<*>? = null) {
  var type: ElementType? = null
  var key: Any? = null
  val children: MutableList<Element> = arrayListOf()
  var _props: T? = null
  var props: T get() = _props!!
  set(value) { _props = value }

  fun child(e: Element) {
    children.add(e.copy(
        index = children.size,
        key = e.key ?: children.size))
  }

  fun done() {
    parent?.child(build())
  }

  fun build(): Element {
    val result = Element(
        type = type!!,
        key = key,
        children = children,
        props = props,
        index = 0)
    if (children.distinctBy { it.key }.count() != children.count()) {
      throw IllegalArgumentException("not unique keys in element $result")
    }
    return result
  }
}

fun <P : Any> primitiveComponent(type: String): ElementBuilder<*>.(p: P, ElementBuilder<P>.() -> Unit) -> Unit =
    { p, bb ->
      val builder = ElementBuilder<P>(this)
      builder.props = p
      builder.bb()
      builder.type = ElementType(type)
      builder.done()
    }

fun<P: Any> component(type: String,
                      body: ElementBuilder<P>.(P, List<Element>) -> Unit): ElementBuilder<*>.(p: P, ElementBuilder<P>.() -> Unit) -> Unit =
    statefulComponent(type, {body})

inline fun<P: Any> buildElement(p: P, bb: ElementBuilder<P>.() -> Unit): Element {
  val eb = ElementBuilder<P>()
  eb.props = p
  eb.bb()
  return eb.children.last()
}

fun<P: Any> statefulComponent(type: String,
                              body: () -> ElementBuilder<P>.(P, List<Element>) -> Unit):
  ElementBuilder<*>.(p: P, ElementBuilder<P>.() -> Unit) -> Unit =
   { p, bb ->
     val elementBuilder = ElementBuilder<P>(this)
     elementBuilder.props = p
     elementBuilder.bb()
     elementBuilder.type = ElementType(
         type = type,
         renderFn = {
           val renderFn = body();
           { p, children ->
             val eb = ElementBuilder<P>()
             @Suppress("UNCHECKED_CAST")
             eb.renderFn(p as P, children)
             eb.children.last()
           }
         })
     elementBuilder.done()
   }

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
    toolkit.performUpdates(listOf(RemoveChild(parent = root, child = rootC!!.node)), root)
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
fun <T : Any?> cell(f: Supplier<T>): Cell<T> = DerivedCell { f.get() }
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
  var props: T? = null

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
        props = props!!,
        index = 0)
    if (children.distinctBy { it.key }.count() != children.count()) {
      throw IllegalArgumentException("not unique keys in element $result")
    }
    return result
  }
}

fun <P : Any> primitiveComponent(type: String): ElementBuilder<*>.(ElementBuilder<P>.() -> Unit) -> Unit =
    { bb ->
      val builder = ElementBuilder<P>(this)
      builder.bb()
      builder.type = ElementType(type)
      builder.done()
    }

fun<P: Any> component(type: String,
                      body: ElementBuilder<P>.(P, List<Element>) -> Unit): ElementBuilder<*>.(ElementBuilder<P>.() -> Unit) -> Unit =
    statefulComponent(type, {body})

inline fun<T: Any> buildElement(bb: ElementBuilder<T>.() -> Unit): Element {
  val eb = ElementBuilder<T>()
  eb.bb()
  return eb.children.last()
}

fun<P: Any> statefulComponent(type: String,
                              body: () -> ElementBuilder<P>.(P, List<Element>) -> Unit): ElementBuilder<*>.(ElementBuilder<P>.() -> Unit) -> Unit =
   { bb ->
     val elementBuilder = ElementBuilder<P>(this)
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


//fun main(args: Array<String>) {
//
//
//  val counter = cell(5)
//  val cbEnabled = cell(true)
//  val cbSelected = cell(true)
//  val allOn = cell { cbEnabled.value && cbSelected.value && counter.value > 6}
//
//  data class ControlProps(val c: VarCell<Int>)
//
//  val controls = component<ControlProps>("controls") { cp, ch ->
//    panel {
//      props = Panel()
//
//      button {
//        props = Button(text = "Inc",
//                       onClick = { cp.c.value++ })
//      }
//      button {
//        props = Button(text = "Dec",
//                       onClick = { cp.c.value-- })
//      }
//    }
//  }
//
//  val rootComponent = component<Unit>("root") { u, ch ->
//    panel {
//      props = Panel()
//      label {
//        key = "label"
//        props = Label(text = if (allOn.value) "ON" else "OFF")
//      }
//      checkbox {
//        key = "master"
//        props = Checkbox(text = "enabled",
//                         selected = cbEnabled.value,
//                         onChange = { cbEnabled.value = it })
//      }
//      for (i in 0..counter.value) {
//        checkbox {
//          key = i
//          props = Checkbox(text = "$i",
//                           selected = cbSelected.value,
//                           enabled = cbEnabled.value,
//                           onChange = {cbSelected.value = it})
//        }
//      }
//      controls {
//        key = "controls"
//        props = ControlProps(c = counter)
//      }
//    }
//  }
//  val jFrame = JFrame()
//  val panel = JPanel()
//  jFrame.contentPane = panel
//
//  mount(Disposer.newDisposable(),
//        buildElement<Unit> { rootComponent { props = Unit } },
//        panel,
//        SwingToolkit())
//  jFrame.pack()
//  jFrame.setVisible(true)
//
//  val jFrame2 = JFrame()
//  val panel2 = JPanel()
//  jFrame2.contentPane = panel2
//
//  mount(Disposer.newDisposable(),
//        buildElement<Unit> {
//          rootComponent { props = Unit }
//        },
//        panel2,
//        SwingToolkit())
//  jFrame2.pack()
//  jFrame2.setVisible(true)
//}



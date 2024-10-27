// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.kernel.rete.impl

import java.util.Collections
import java.util.function.BiConsumer
import java.util.function.Consumer

internal inline fun <K, V> MutableMap<K, V>.removeIf(key: K, p: (V) -> Boolean) {
  get(key)?.let { value ->
    if (p(value)) {
      remove(key)
    }
  }
}

private const val EMPTY_STATE = 0
private const val ONE_STATE = 1
private const val LIST_STATE = 2
private const val SET_STATE = 3

internal fun <T> adaptiveSetOf(x: T): MutableSet<T> =
  adaptiveSetOf<T>().apply { add(x) }

internal fun <T> adaptiveSetOf(): MutableSet<T> =
  AdaptiveSet()

internal open class AdaptiveSet<T> : MutableSet<T> {
  var subs: Any? = null
  var state: Int = EMPTY_STATE

  override fun add(element: T): Boolean =
    when (state) {
      EMPTY_STATE -> {
        this.subs = element
        state = ONE_STATE
        true
      }
      ONE_STATE -> {
        when {
          subs != element -> {
            subs = arrayListOf(subs as T, element)
            state = LIST_STATE
            true
          }
          else -> false
        }
      }
      LIST_STATE -> {
        val subs = subs as ArrayList<T>
        when {
          !subs.contains(element) -> {
            when {
              subs.size == 8 -> {
                this.subs = HashSet(subs).apply { add(element) }
                this.state = SET_STATE
              }
              else -> {
                subs.add(element)
              }
            }
            true
          }
          else -> false
        }
      }
      SET_STATE -> {
        (subs as HashSet<T>).add(element)
      }
      else -> {
        error("unreachable")
      }
    }

  override fun addAll(elements: Collection<T>): Boolean {
    var res = false
    elements.forEach { t ->
      val x = add(t)
      res = res || x
    }
    return res
  }

  override val size: Int
    get() = when (state) {
      EMPTY_STATE -> 0
      ONE_STATE -> 1
      LIST_STATE -> (subs as ArrayList<T>).size
      SET_STATE -> (subs as HashSet<T>).size
      else -> error("unreachable")
    }

  override fun clear() {
    state = EMPTY_STATE
    subs = null
  }

  override fun containsAll(elements: Collection<T>): Boolean =
    elements.all { contains(it) }

  override fun contains(element: T): Boolean =
    when (state) {
      EMPTY_STATE -> false
      ONE_STATE -> subs == element
      LIST_STATE -> (subs as ArrayList<T>).contains(element)
      SET_STATE -> (subs as HashSet<T>).contains(element)
      else -> error("unreachable")
    }

  override fun isEmpty(): Boolean = subs == null

  override fun iterator(): MutableIterator<T> {
    val i = when (state) {
      EMPTY_STATE -> Collections.emptyIterator()
      ONE_STATE -> listOf(subs as T).iterator()
      LIST_STATE -> (subs as ArrayList<T>).iterator()
      SET_STATE -> (subs as HashSet<T>).iterator()
      else -> error("unreachable")
    }
    return object : MutableIterator<T>, Iterator<T> by i {
      override fun remove() {
        TODO("Not yet implemented")
      }
    }
  }

  override fun retainAll(elements: Collection<T>): Boolean {
    TODO("Not yet implemented")
  }

  override fun removeAll(elements: Collection<T>): Boolean {
    var res = false
    elements.forEach { t ->
      val x = remove(t)
      res = res || x
    }
    return res
  }

  override fun remove(element: T): Boolean =
    when (state) {
      EMPTY_STATE -> false
      ONE_STATE -> {
        when {
          element == subs -> {
            subs = null
            state = EMPTY_STATE
            true
          }
          else -> false
        }
      }
      LIST_STATE -> {
        (subs as ArrayList<T>).let { subs ->
          val res = subs.remove(element)
          if (subs.size == 1) {
            this.subs = subs.single()
            state = ONE_STATE
          }
          res
        }
      }
      SET_STATE -> {
        (subs as HashSet<T>).let { subs ->
          val res = subs.remove(element)
          if (subs.size == 8) {
            this.subs = ArrayList(subs)
            state = LIST_STATE
          }
          res
        }
      }
      else -> {
        error("unreachable")
      }
    }

  override fun forEach(action: Consumer<in T>) {
    when (state) {
      EMPTY_STATE -> {}
      ONE_STATE -> action.accept(subs as T)
      LIST_STATE -> (subs as ArrayList<T>).forEach(action)
      SET_STATE -> (subs as HashSet<T>).forEach(action)
      else -> error("unreachable")
    }
  }
}

private fun <K, V> checkingMap(impl: MutableMap<K, V>, gold: MutableMap<K, V>): MutableMap<K, V> =
  object : MutableMap<K, V> {
    fun <T> check(t1: T, t2: T): T = t1.also {
      if (t1 != t2) {
        println(Throwable("$t1 != $t2"))
      }
    }

    override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
      get() = impl.entries //check(impl.entries, gold.entries)
    override val keys: MutableSet<K>
      get() = check(impl.keys, gold.keys)
    override val size: Int
      get() = check(impl.size, gold.size)
    override val values: MutableCollection<V>
      get() = check(impl.values.toSet(), gold.values.toSet()).let { impl.values }

    override fun clear() {
      impl.clear()
      gold.clear()
    }

    override fun isEmpty(): Boolean = check(impl.isEmpty(), gold.isEmpty())

    override fun remove(key: K): V? = check(impl.remove(key), gold.remove(key))

    override fun putAll(from: Map<out K, V>) {
      impl.putAll(from)
      gold.putAll(from)
    }

    override fun put(key: K, value: V): V? = check(impl.put(key, value), gold.put(key, value))

    override fun get(key: K): V? = check(impl.get(key), gold.get(key))

    override fun containsValue(value: V): Boolean = check(impl.containsValue(value), gold.containsValue(value))

    override fun containsKey(key: K): Boolean = check(impl.containsKey(key), gold.containsKey(key))
  }

internal fun <K, V> adaptiveMapOf(k: K, v: V): MutableMap<K, V> =
  adaptiveMapOf<K, V>().apply { put(k, v) }

internal fun <K, V> adaptiveMapOf(): MutableMap<K, V> =
  //    checkingMap(AdaptiveMap(), hashMapOf())
  AdaptiveMap()
//  hashMapOf()

internal class AdaptiveMap<K, V> : MutableMap<K, V> {
  var state: Int = EMPTY_STATE
  var f1: Any? = null
  var f2: Any? = null

  override fun equals(other: Any?): Boolean =
    other is Map<*, *> && other.entries == entries

  override fun hashCode(): Int = entries.hashCode()

  override fun isEmpty(): Boolean = state == EMPTY_STATE

  override fun putAll(from: Map<out K, V>) {
    from.forEach { (k, v) ->
      put(k, v)
    }
  }

  override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
    get() = TODO("Not yet implemented")

  override val values: MutableCollection<V>
    get() = TODO("Not yet implemented")

  override fun containsValue(value: V): Boolean {
    TODO("Not yet implemented")
  }

  override val keys: MutableSet<K>
    get() =
      Collections.unmodifiableSet(
        when (state) {
          SET_STATE -> (f1 as HashMap<K, V>).keys
          else -> buildSet { forEach { k, v -> add(k) } }
        })

  override val size: Int
    get() = when (state) {
      EMPTY_STATE -> 0
      ONE_STATE -> 1
      LIST_STATE -> (f1 as ArrayList<Any?>).size / 2
      SET_STATE -> (f1 as HashMap<K, V>).size
      else -> error("unreachable")
    }

  override fun clear() {
    f1 = null
    f2 = null
    state = EMPTY_STATE
  }

  override fun containsKey(key: K): Boolean {
    return get(key) != null
  }

  override operator fun get(k: K): V? {
    return when (state) {
      EMPTY_STATE -> null
      ONE_STATE -> (f2 as V).takeIf { k == f1 }
      LIST_STATE -> {
        val r = (f1 as ArrayList<Any?>)
        var i = 0
        while (i < r.size) {
          if (r[i] == k) {
            return r[i + 1] as V
          }
          i += 2
        }
        null
      }
      SET_STATE -> {
        (f1 as HashMap<K, V>)[k]
      }
      else -> error("unreachable")
    }
  }

  override fun put(k: K, v: V): V? {
    return when (state) {
      EMPTY_STATE -> {
        f1 = k
        f2 = v
        state = ONE_STATE
        null
      }
      ONE_STATE -> {
        when {
          k == f1 -> {
            val old = f2 as V
            f2 = v
            old
          }
          else -> {
            f1 = arrayListOf(f1, f2, k, v)
            f2 = null
            state = LIST_STATE
            null
          }
        }
      }
      LIST_STATE -> {
        val r = (f1 as ArrayList<Any?>)
        var i = 0
        while (i < r.size) {
          if (r[i] == k) {
            val oldV = r[i + 1] as V
            r[i + 1] = v
            return oldV
          }
          i += 2
        }

        when {
          r.size == 8 -> {
            val hm = HashMap<K, V>()
            var i = 0
            while (i < r.size) {
              hm.put(r[i] as K, r[i + 1] as V)
              i += 2
            }
            hm.put(k, v)
            f1 = hm
            state = SET_STATE
          }
          else -> {
            r.add(k)
            r.add(v)
          }
        }
        null
      }
      SET_STATE -> {
        (f1 as HashMap<K, V>).put(k, v)
      }
      else -> {
        error("unreachable")
      }
    }
  }

  override fun remove(k: K): V? {
    return when (state) {
      EMPTY_STATE -> null
      ONE_STATE -> {
        when {
          f1 == k -> {
            val v = f2 as V
            f1 = null
            f2 = null
            state = EMPTY_STATE
            v
          }
          else -> null
        }
      }
      LIST_STATE -> {
        val r = (f1 as ArrayList<Any?>)
        var i = 0
        while (i < r.size) {
          if (r[i] == k) {
            val v = r[i + 1] as V
            r.removeAt(i + 1)
            r.removeAt(i)
            if (r.size == 2) {
              f1 = r[0]
              f2 = r[1]
              state = ONE_STATE
            }
            return v
          }
          i += 2
        }
        null
      }
      SET_STATE -> {
        (f1 as HashMap<K, V>).let { map ->
          val res = map.remove(k)
          if (map.size == 8 / 2) {
            state = LIST_STATE
            f1 = ArrayList<Any?>().apply {
              map.forEach { (k, v) ->
                add(k)
                add(v)
              }
            }
          }
          res
        }
      }
      else -> error("unreachable")
    }
  }

  override fun forEach(action: BiConsumer<in K, in V>) {
    when (state) {
      EMPTY_STATE -> {}
      ONE_STATE -> action.accept(f1 as K, f2 as V)
      LIST_STATE -> {
        val r = (f1 as ArrayList<Any?>)
        var i = 0
        while (i < r.size) {
          action.accept(r[i] as K, r[i + 1] as V)
          i += 2
        }
      }
      SET_STATE -> {
        (f1 as HashMap<K, V>).forEach(action)
      }
      else -> error("unreachable")
    }
  }
}

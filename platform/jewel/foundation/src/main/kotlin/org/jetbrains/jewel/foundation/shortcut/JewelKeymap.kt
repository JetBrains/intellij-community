// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation.shortcut

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi

/**
 * A named keyboard scheme: effective bindings are this scheme's own bindings applied over its [parent]'s, with explicit
 * hide markers able to remove inherited bindings (the standalone analogue of IJPL keymap inheritance).
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
public interface JewelKeymap {
    /** Unique name identifying this keymap scheme. */
    public val name: String

    /** Optional parent scheme from which unbound actions inherit shortcuts. */
    public val parent: JewelKeymap?

    /** Increments on every mutation of this scheme (not of its parent). */
    public val modificationCount: StateFlow<Long>

    /** Returns all effective key sequences bound to [action]. */
    public fun shortcutsFor(action: JewelActionId): List<JewelKeySequence>

    /** Returns all action IDs bound to [sequence]. */
    public fun actionIdsFor(sequence: JewelKeySequence): List<JewelActionId>

    /** Action IDs having a two-stroke sequence whose first stroke is [first]; needed for chord dispatch. */
    public fun actionIdsForPrefix(first: JewelKeyStroke): List<JewelActionId>

    /** Returns all action IDs known to this scheme or its parent chain. */
    public fun actionIds(): Set<JewelActionId>

    /** Returns action IDs that conflict with [sequence]. */
    public fun conflicts(sequence: JewelKeySequence): List<JewelActionId> = actionIdsFor(sequence)
}

/** [JewelKeymap] that supports adding, removing, hiding, and replacing shortcut bindings. */
@ApiStatus.Experimental
@ExperimentalJewelApi
public interface MutableJewelKeymap : JewelKeymap {
    /** Adds [sequence] to the bindings for [action]. */
    public fun bind(action: JewelActionId, sequence: JewelKeySequence)

    /** Removes [sequence] from this scheme's own bindings for [action]. */
    public fun unbind(action: JewelActionId, sequence: JewelKeySequence)

    /** Hides a binding inherited from the parent chain without touching the parent scheme. */
    public fun hideInherited(action: JewelActionId, sequence: JewelKeySequence)

    /** Replaces all effective bindings for [action] with [sequences]. */
    public fun replaceBindings(action: JewelActionId, sequences: List<JewelKeySequence>)

    /** Clears all effective bindings for [action], suppressing inherited ones as well. */
    public fun removeAllBindings(action: JewelActionId)
}

/** Thread-safe in-memory implementation of [MutableJewelKeymap]. */
@ApiStatus.Experimental
@ExperimentalJewelApi
public class InMemoryJewelKeymap(override val name: String, override val parent: JewelKeymap? = null) :
    MutableJewelKeymap {
    private val own = LinkedHashMap<JewelActionId, MutableList<JewelKeySequence>>()
    private val hidden = LinkedHashMap<JewelActionId, MutableSet<JewelKeySequence>>()
    private val replaced = mutableSetOf<JewelActionId>()

    private val _modificationCount = MutableStateFlow(0L)
    override val modificationCount: StateFlow<Long> = _modificationCount.asStateFlow()

    private fun touch() {
        _modificationCount.update { it + 1 }
    }

    @Synchronized
    override fun shortcutsFor(action: JewelActionId): List<JewelKeySequence> {
        val inherited =
            if (action in replaced) {
                emptyList()
            } else {
                (parent?.shortcutsFor(action).orEmpty()).filterNot { it in hidden[action].orEmpty() }
            }
        return (inherited + own[action].orEmpty()).distinct()
    }

    @Synchronized
    override fun actionIdsFor(sequence: JewelKeySequence): List<JewelActionId> =
        actionIds().filter { sequence in shortcutsFor(it) }

    @Synchronized
    override fun actionIdsForPrefix(first: JewelKeyStroke): List<JewelActionId> =
        actionIds().filter { id -> shortcutsFor(id).any { it.second != null && it.first == first } }

    @Synchronized override fun actionIds(): Set<JewelActionId> = (parent?.actionIds().orEmpty()) + own.keys

    @Synchronized
    override fun bind(action: JewelActionId, sequence: JewelKeySequence) {
        val list = own.getOrPut(action) { mutableListOf() }
        if (sequence !in list) {
            list.add(sequence)
        }
        touch()
    }

    @Synchronized
    override fun unbind(action: JewelActionId, sequence: JewelKeySequence) {
        own[action]?.remove(sequence)
        touch()
    }

    @Synchronized
    override fun hideInherited(action: JewelActionId, sequence: JewelKeySequence) {
        hidden.getOrPut(action) { mutableSetOf() }.add(sequence)
        touch()
    }

    @Synchronized
    override fun replaceBindings(action: JewelActionId, sequences: List<JewelKeySequence>) {
        replaced.add(action)
        own[action] = sequences.distinct().toMutableList()
        touch()
    }

    @Synchronized
    override fun removeAllBindings(action: JewelActionId) {
        replaced.add(action)
        own[action] = mutableListOf()
        touch()
    }

    /** Factory methods for constructing [InMemoryJewelKeymap] instances. */
    public companion object {
        /** Base scheme seeded from the registered action definitions' default shortcuts. */
        public fun fromDefaults(name: String, registry: JewelActionRegistry): InMemoryJewelKeymap {
            val keymap = InMemoryJewelKeymap(name)
            for (definition in registry.definitions()) {
                for (sequence in definition.defaultShortcuts) {
                    keymap.bind(definition.action.id, sequence)
                }
            }
            return keymap
        }
    }
}

/** Coordinates the available keymap schemes and tracks the currently active [JewelKeymap]. */
@ApiStatus.Experimental
@ExperimentalJewelApi
public interface JewelKeymapManager {
    /** Observable flow of the active keymap. */
    public val activeKeymap: StateFlow<JewelKeymap>

    /** Observable flow of all registered keymap schemes. */
    public val keymaps: StateFlow<List<JewelKeymap>>

    /** Adds or replaces a keymap scheme by name. */
    public fun addKeymap(keymap: JewelKeymap)

    /** Removes the inactive keymap named [name]. */
    public fun removeKeymap(name: String)

    /** Activates the registered keymap named [name]. */
    public fun setActiveKeymap(name: String)
}

/** Default in-memory [JewelKeymapManager] initialized with an [initial] active keymap. */
@ApiStatus.Experimental
@ExperimentalJewelApi
public class DefaultJewelKeymapManager(initial: JewelKeymap) : JewelKeymapManager {
    private val lock = Any()
    private val _keymaps = MutableStateFlow(listOf(initial))
    private val _activeKeymap = MutableStateFlow(initial)

    override val activeKeymap: StateFlow<JewelKeymap> = _activeKeymap.asStateFlow()
    override val keymaps: StateFlow<List<JewelKeymap>> = _keymaps.asStateFlow()

    override fun addKeymap(keymap: JewelKeymap) {
        synchronized(lock) {
            _keymaps.update { current -> current.filterNot { it.name == keymap.name } + keymap }
            if (_activeKeymap.value.name == keymap.name) {
                _activeKeymap.value = keymap
            }
        }
    }

    override fun removeKeymap(name: String) {
        synchronized(lock) {
            require(_activeKeymap.value.name != name) { "Cannot remove the active keymap '$name'" }
            _keymaps.update { current -> current.filterNot { it.name == name } }
        }
    }

    override fun setActiveKeymap(name: String) {
        synchronized(lock) {
            val keymap = _keymaps.value.firstOrNull { it.name == name }
            requireNotNull(keymap) { "Unknown keymap '$name'" }
            _activeKeymap.value = keymap
        }
    }
}

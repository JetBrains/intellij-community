// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation.shortcut

import com.intellij.platform.icons.IconDescriptor
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi

/** Stable identifier for a Jewel action; use reverse-DNS names for application commands. */
@ApiStatus.Experimental
@ExperimentalJewelApi
@JvmInline
public value class JewelActionId(
    /** String identifier of the action. */
    public val value: String
)

/** Behavioral kind of a [JewelAction]. */
@ApiStatus.Experimental
@ExperimentalJewelApi
public enum class JewelActionKind {
    /** Stateless action invoked on demand. */
    Command,

    /** Stateful action that maintains a selected or unselected toggle state. */
    Toggle,
}

/**
 * Host-neutral action identity plus its [template] presentation, mirroring IJPL's split between an `AnAction` and its
 * template `Presentation`: identity, kind and shortcut semantics belong to the action; anything renderable belongs to
 * the template, and anything per-place belongs to a sampled [ActionPresentation]. Immutable — dynamic state lives on
 * bindings.
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
public data class JewelAction(
    /** Unique action identifier. */
    val id: JewelActionId,
    /** Default authored template presentation. */
    val template: ActionTemplatePresentation,
    /** Optional category name for grouping in keymaps or action lists. */
    val category: String? = null,
    /** Whether the action behaves as a command or a toggle. */
    val kind: JewelActionKind = JewelActionKind.Command,
) {
    /** Declares an action from its authored defaults directly, the common case. */
    public constructor(
        id: JewelActionId,
        title: String,
        category: String? = null,
        kind: JewelActionKind = JewelActionKind.Command,
        icon: IconDescriptor? = null,
        description: String? = null,
    ) : this(id, ActionTemplatePresentation(title, description, icon), category, kind)

    /** The template's text: the action's authored name, before any per-place override. */
    public val title: String
        get() = template.text
}

/** The complete host-neutral catalog item: semantics plus default keyboard bindings. */
@ApiStatus.Experimental
@ExperimentalJewelApi
public data class JewelActionDefinition(
    /** Action identity and template presentation. */
    val action: JewelAction,
    /** Default keyboard sequences bound to the action. */
    val defaultShortcuts: List<JewelKeySequence> = emptyList(),
)

/** Handle for a registered [JewelActionDefinition] that releases the registration when closed. */
@ApiStatus.Experimental
@ExperimentalJewelApi
public interface ActionRegistration : AutoCloseable {
    /** Action registered by this handle. */
    public val action: JewelAction

    override fun close()
}

/**
 * Registry of action definitions for one host. In standalone it is the application action catalog; the IJPL bridge
 * attaches definitions to matching declared bridge actions or registers runtime ones.
 *
 * Identical duplicate registrations are reference-counted; a differing descriptor for an already-registered ID fails
 * with [IllegalStateException].
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
public interface JewelActionRegistry {
    /** Registers [definition] and returns a handle that releases the reference when closed. */
    public fun register(definition: JewelActionDefinition): ActionRegistration

    /** Looks up the registered definition for [id], or returns `null`. */
    public fun definition(id: JewelActionId): JewelActionDefinition?

    /** Returns a snapshot list of all currently registered action definitions. */
    public fun definitions(): List<JewelActionDefinition>

    /**
     * The action's current per-place state, for registries backed by a host that computes one — in the IJPL bridge, the
     * result of the platform's `AnAction.update()`. Returning `null` (the default) means this registry only knows the
     * static template, which is the right answer for a standalone catalog.
     *
     * This is the third layer of the presentation model: [definition]'s template supplies the authored defaults, this
     * supplies what the host recomputed, and a focused binding's override is applied last. Without it, a control bound
     * to a platform action would render its authored icon and text but never reflect anything `update()` decided —
     * enablement, visibility, or toggle state.
     *
     * **Threading:** called during presentation sampling; bridge implementations schedule platform updates
     * asynchronously via the platform updater and return cached samples off-EDT.
     */
    public fun sampledPresentation(id: JewelActionId): ActionPresentation? = null

    /**
     * Returns the host keymap shortcuts bound to [id], or `null` when the host resolves shortcuts from its own
     * [JewelKeymap].
     */
    public fun shortcutsFor(id: JewelActionId): List<JewelKeySequence>? = null
}

/** Thread-safe in-memory [JewelActionRegistry] with reference-counted duplicate registrations. */
@ApiStatus.Experimental
@ExperimentalJewelApi
public class DefaultJewelActionRegistry : JewelActionRegistry {
    private class Entry(val definition: JewelActionDefinition) {
        var refCount: Int = 0
    }

    private val entries = ConcurrentHashMap<JewelActionId, Entry>()
    private val registrationListeners = CopyOnWriteArrayList<WeakReference<(JewelActionId) -> Unit>>()

    internal fun addRegistrationListener(listener: (JewelActionId) -> Unit) {
        registrationListeners.add(WeakReference(listener))
    }

    private fun notifyRegistrationChanged(id: JewelActionId) {
        var hasDeadRefs = false
        for (ref in registrationListeners) {
            val listener = ref.get()
            if (listener != null) {
                listener(id)
            } else {
                hasDeadRefs = true
            }
        }
        if (hasDeadRefs) {
            registrationListeners.removeIf { it.get() == null }
        }
    }

    override fun register(definition: JewelActionDefinition): ActionRegistration {
        val id = definition.action.id
        var added = false
        synchronized(entries) {
            val existing = entries[id]
            if (existing != null) {
                check(existing.definition == definition) {
                    "Action '${id.value}' is already registered with a different descriptor"
                }
                existing.refCount++
            } else {
                entries[id] = Entry(definition).also { it.refCount = 1 }
                added = true
            }
        }
        if (added) {
            notifyRegistrationChanged(id)
        }

        val closed = AtomicBoolean(false)
        return object : ActionRegistration {
            override val action: JewelAction = definition.action

            override fun close() {
                if (!closed.compareAndSet(false, true)) return
                var removed = false
                synchronized(entries) {
                    val entry = entries[id] ?: return
                    entry.refCount--
                    if (entry.refCount <= 0) {
                        entries.remove(id)
                        removed = true
                    }
                }
                if (removed) {
                    notifyRegistrationChanged(id)
                }
            }
        }
    }

    override fun definition(id: JewelActionId): JewelActionDefinition? = entries[id]?.definition

    override fun definitions(): List<JewelActionDefinition> = entries.values.map { it.definition }
}

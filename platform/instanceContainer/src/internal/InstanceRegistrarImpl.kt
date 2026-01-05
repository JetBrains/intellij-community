// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.instanceContainer.internal

import com.intellij.openapi.diagnostic.trace
import com.intellij.platform.instanceContainer.InstanceNotOverridableException
import com.intellij.platform.instanceContainer.InstanceNotRegisteredException

internal class InstanceRegistrarImpl(
  private val debugString: String,
  private val existingKeys: Map<String, InstanceHolder>,
  // at the moment, this flag only changes log level error->warn but does not prevent incorrect registration
  private val shouldTolerateIncorrectOverrides: Boolean,
  private val completion: (Map<String, RegistrationAction>) -> UnregisterHandle?,
) : InstanceRegistrar {
  private var _actions: MutableMap<String, RegistrationAction>? = LinkedHashMap()
  private fun actions(): MutableMap<String, RegistrationAction> = checkNotNull(_actions) {
    "$debugString : instance registrar is already completed"
  }

  override fun toString(): String {
    val stateString = _actions?.let { "size: ${it.size}" } ?: "completed"
    return "$debugString instance registrar ($stateString)"
  }

  override fun complete(): UnregisterHandle? {
    val actions = actions()
    _actions = null
    return completion(actions)
  }

  override fun registerInitializer(keyClassName: String, initializer: InstanceInitializer) {
    val actions = actions()
    val existingHolder = existingKeys[keyClassName]
    if (existingHolder != null) {
      LOG.error(InstanceAlreadyRegisteredException(
        keyClassName,
        existingInstanceClassName = existingHolder.instanceClassName(),
        newInstanceClassName = initializer.instanceClassName,
      ))
      return
    }
    when (val existingAction = actions[keyClassName]) {
      null -> actions[keyClassName] = RegistrationAction.Register(initializer)
      is RegistrationAction.Register -> LOG.error(InstanceAlreadyRegisteredException(
        keyClassName,
        existingInstanceClassName = existingAction.initializer.instanceClassName,
        newInstanceClassName = initializer.instanceClassName,
      ))
      is RegistrationAction.Override -> error("must not happen unless keyClassName is in existingKeys which is false") // sanity check
      RegistrationAction.Remove -> error("must not happen unless keyClassName is in existingKeys which is false") // sanity check
    }
  }

  override fun overrideInitializer(keyClassName: String, initializer: InstanceInitializer?) {
    val actions = actions()
    val existingAction = actions[keyClassName]

    val newAction = when (existingAction) {
      null -> {
        val existingInstanceHolder = existingKeys[keyClassName]
        if (existingInstanceHolder == null) {
          LOG.error(InstanceNotRegisteredException("$keyClassName -> ${initializer?.instanceClassName ?: "<removed>"}"))
          return
        }
        if (!existingInstanceHolder.overridable) {
          val exception =
            InstanceNotOverridableException("$keyClassName -> existing: ${existingInstanceHolder.instanceClassName()}, new: ${initializer?.instanceClassName ?: "<removed>"}")
          logIncorrectOverride(exception)
        }
        if (initializer == null) RegistrationAction.Remove else RegistrationAction.Override(initializer)
      }
      is RegistrationAction.Register -> {
        check(keyClassName !in existingKeys) // sanity check
        if (!existingAction.initializer.overridable) {
          val exception =
            InstanceNotOverridableException("$keyClassName -> existing: ${existingAction.initializer.instanceClassName}, new: ${initializer?.instanceClassName ?: "<removed>"}")
          logIncorrectOverride(exception)
        }
        LOG.trace {
          "$debugString : $keyClassName is registered and overridden in the same scope " +
          "(${existingAction.initializer.instanceClassName} -> ${initializer?.instanceClassName ?: "<removed>"})"
        }
        if (initializer == null) {
          actions.remove(keyClassName)
          return
        }
        else {
          RegistrationAction.Register(initializer)
        }
      }
      is RegistrationAction.Override -> {
        check(keyClassName in existingKeys) // sanity check
        if (!existingAction.initializer.overridable) {
          val exception =
            InstanceNotOverridableException("$keyClassName -> existing: ${existingAction.initializer.instanceClassName}, new: ${initializer?.instanceClassName ?: "<removed>"}")
          logIncorrectOverride(exception)
        }
        LOG.trace {
          "$debugString : $keyClassName is overridden again in the same scope " +
          "(${existingAction.initializer.instanceClassName} -> ${initializer?.instanceClassName ?: "<removed>"})"
        }
        if (initializer == null) RegistrationAction.Remove else RegistrationAction.Override(initializer)
      }
      is RegistrationAction.Remove -> {
        check(keyClassName in existingKeys) // sanity check
        // TODO LOG.error(InstanceNotRegisteredException("$keyClassName -> ${initializer?.instanceClassName ?: "<removed>"}"))
        LOG.trace {
          "$debugString : $keyClassName is removed and overridden again in the same scope " +
          "(<removed> -> ${initializer?.instanceClassName})"
        }
        if (initializer == null) RegistrationAction.Remove else RegistrationAction.Override(initializer)
      }
    }
    actions[keyClassName] = newAction
  }

  private fun logIncorrectOverride(exception: InstanceNotOverridableException) {
    if (shouldTolerateIncorrectOverrides) {
      LOG.warn(exception)
    }
    else {
      LOG.error(exception)
    }
  }
}

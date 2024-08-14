// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.kernel.util

import com.intellij.platform.kernel.KernelService
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jetbrains.rhizomedb.EID
import com.jetbrains.rhizomedb.entity
import com.jetbrains.rhizomedb.lookupOne
import fleet.kernel.*
import fleet.rpc.core.Serialization
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import kotlinx.coroutines.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1
import kotlin.reflect.KType
import kotlin.reflect.typeOf

abstract class SharedModel(uniqueId: String = "") {
  private val registeredLambdas = IntOpenHashSet()
  private val modelCreated: Deferred<*>
  val id: String = this.javaClass.name + uniqueId
  internal val serialization = Serialization()

  init {
    modelCreated = KernelService.instance.kernel.changeAsync {
      shared {
        if (lookupOne(ViewModelEntity::modelId, id) == null) {
          new(ViewModelEntity::class) {
            this.modelId = id
          }
        }
      }
    }
  }

  @RequiresEdt
  fun dispose() {
    val instance = KernelService.instance.kernel
    for (lambdaId in registeredLambdas) {
      ReadTracker.forget(lambdaId)
    }
    instance.kernel.changeAsync {
      shared {
        lookupOne(ViewModelEntity::modelId, id)?.delete()
      }
    }
  }

  private inner class ModelPropertyDelegateImpl<T : Any>(val ktype: KType, val defaultValue: T) : ModelPropertyDelegate<T> {
    private val serializer : KSerializer<T> = serialization.kSerializer(ktype) as KSerializer<T>
    private var eid: EID? = null
    private var jsonCache: JsonElement? = null
    private var value: T? = null
    private var fqn: String? = null

    override operator fun getValue(thisRef: SharedModel, property: KProperty<*>): T {
      eid?.let { eid ->
        (entity(eid) as? ModelPropertyEntity)?.let { modelProperty ->
          return getByEntity(modelProperty)
        }
      }
      if (fqn == null) {
        fqn = id + "." + property.name
      }
      val modelPropertyEntity = lookupOne(ModelPropertyEntity::id, fqn!!)
      if (modelPropertyEntity == null) {
        return defaultValue
      }
      eid = modelPropertyEntity.eid
      return getByEntity(modelPropertyEntity)
    }

    private fun getByEntity(modelPropertyEntity: ModelPropertyEntity): T {
      if (jsonCache == modelPropertyEntity.value && value != null) {
        return value!!
      }
      jsonCache = modelPropertyEntity.value
      value = serialization.json.decodeFromJsonElement(serializer, modelPropertyEntity.value)
      return value!!
    }
  }

  interface ModelPropertyDelegate<T : Any> {
    operator fun getValue(thisRef: SharedModel, property: KProperty<*>): T
  }

  fun <T : Any> modelProperty(ktype: KType, defaultValue: T): ModelPropertyDelegate<T> {
    return ModelPropertyDelegateImpl(ktype, defaultValue)
  }

  inline fun <reified T : Any> modelProperty(defaultValue: T): ModelPropertyDelegate<T> {
    return modelProperty(typeOf<T>(), defaultValue)
  }

  fun changeAsync(f: ModelChangeScope.() -> Unit): Deferred<*> {
    return KernelService.instance.kernel.changeAsync {
      shared {
        with(ModelChangeScopeImpl(this)) {
          f()
        }
      }
    }
  }

  suspend fun change(f: ModelChangeScope.() -> Unit) {
    KernelService.instance.kernel.changeSuspend {
      shared {
        with(ModelChangeScopeImpl(this)) {
          f()
        }
      }
    }
  }

  @RequiresEdt
  fun reactive(f: () -> Unit) {
    registeredLambdas.add(ReadTracker.reactive(f))
  }

  fun launch(f: suspend CoroutineScope.() -> Unit) {
    val instance = KernelService.instance
    instance.kernel.coroutineScope.launch {
      supervisorScope {
        withContext(instance.coroutineContext) {
          modelCreated.await()
          lookupOne(ViewModelEntity::modelId, id)?.let { viewModelEntity ->
            withCondition({lookupOne(ViewModelEntity::modelId, id) != null}) {
              f()
            }
          }
        }
      }
    }
  }
}

sealed class ModelChangeScope {
  abstract fun <M : SharedModel, V : Any> M.set(property: KProperty1<M, V>, ktype: KType, value: V)

  inline fun <M : SharedModel, reified V : Any> M.set(property: KProperty1<M, V>, value: V) {
    set(property, typeOf<V>(), value)
  }
}

private class ModelChangeScopeImpl(val sharedChangeScope: SharedChangeScope) : ModelChangeScope() {
  override fun <M : SharedModel, V : Any> M.set(property: KProperty1<M, V>,
                                                ktype: KType,
                                                value: V) {
    with(sharedChangeScope) {
      val fqn = id + "." + property.name
      val value = serialization.json.encodeToJsonElement(serialization.kSerializer(ktype), value)
      val viewModel = lookupOne(ViewModelEntity::modelId, id)
      if (viewModel ==  null) {
        throw IllegalStateException("ViewModelEntity not found for model $id")
      }
      val alreadyExist = lookupOne(ModelPropertyEntity::id, fqn)
      withKey(fqn) {
        alreadyExist?.let { it.value = value } ?: new(ModelPropertyEntity::class) {
          this.id = fqn
          this.viewModelEntity = viewModel
          this.value = value
        }
      }
    }
  }
}
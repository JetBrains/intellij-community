// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointName
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModuleBuilder
import kotlin.reflect.KClass

interface ProjectExtras<TData : Any> {
  companion object {
    val EP_NAME = ExtensionPointName.create<ProjectExtras<*>>("project.extras")
  }

  val id: String get() = javaClass.name

  /**
   * Called from the backend side
   */
  fun getValues(project: Project): Flow<TData>

  /**
   * Called from the frontend side
   */
  // TODO maybe make it suspend?
  fun emitValue(project: Project, value: TData)

  fun emitValueAny(project: Project, value: Any) {
    emitValue(project, value as TData)
  }

  // probably we can use it for serializers
  val dataClass: KClass<TData>

  // or register them manually
  fun registerSerializers(moduleBuilder: SerializersModuleBuilder)
}

// **************
// It's an API for making services with auto-synced data and a sample implementation

/*
  T must be serializable by kotlinx serialization
 */
interface RemotedStateService<T> {
  val value: MutableStateFlow<T>

// TODO: seems we have to provide an API for configuring serialization of T
}

open class RemotedStateServiceBinder<T : Any/*, TService : RemotedStateService<T>*/>(val instance: (Project) -> RemotedStateService<T>/*, val klass: KClass<TService>*/) : ProjectExtras<T> {
  override fun getValues(project: Project): Flow<T> {
    // TODO: choose the approach
    return instance(project).value
    //return project.getService(klass.java).value
  }

  override fun emitValue(project: Project, value: T) {
    // TODO: choose the approach
    instance(project).value.tryEmit(value)
    // project.getService(klass.java).value.tryEmit(value)
  }

  override val dataClass: KClass<T>
    get() = TODO("Not yet implemented")

  override fun registerSerializers(moduleBuilder: SerializersModuleBuilder) {
    TODO("Not yet implemented")
  }
}


@Service(Service.Level.PROJECT)
class SampleRemoteStateService(val project: Project): RemotedStateService<SampleRemoteStateService.MyData> {
  companion object {
    fun getInstance(project: Project): SampleRemoteStateService = project.service()
  }

  // TODO: plugin is not enabled
  @Serializable
  class MyData

  override val value: MutableStateFlow<MyData> = MutableStateFlow(MyData())

  // TODO: register as ProjectExtras
  // TODO: choose approach of getting an instance
  class MyBinder : RemotedStateServiceBinder<MyData>(::getInstance/*, ProjectExtrasService::class*/)
}
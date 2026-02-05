// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl

import com.intellij.platform.icons.layers.IconLayer
import kotlin.reflect.KClass
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.modules.SerializersModuleBuilder
import kotlinx.serialization.serializer

@OptIn(InternalSerializationApi::class)
fun <TLayer : IconLayer> SerializersModuleBuilder.iconLayer(
    klass: KClass<TLayer>,
    serializer: KSerializer<TLayer>? = null,
) {
    polymorphic(IconLayer::class, klass, serializer ?: klass.serializer())
}

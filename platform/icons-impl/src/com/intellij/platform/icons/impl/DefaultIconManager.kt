// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl

import com.intellij.platform.icons.DeferredIcon
import com.intellij.platform.icons.Icon
import com.intellij.platform.icons.IconIdentifier
import com.intellij.platform.icons.IconManager
import com.intellij.platform.icons.design.IconDesigner
import com.intellij.platform.icons.design.SvgPatcherDesigner
import com.intellij.platform.icons.design.UnitsFactory
import com.intellij.platform.icons.filters.ColorFilterFactory
import com.intellij.platform.icons.impl.filters.DefaultColorFilterFactory
import com.intellij.platform.icons.impl.layers.AnimatedIconLayer
import com.intellij.platform.icons.impl.layers.ImageIconLayer
import com.intellij.platform.icons.impl.layers.LayoutIconLayer
import com.intellij.platform.icons.impl.layers.NestedIconLayer
import com.intellij.platform.icons.impl.layers.ShapeIconLayer
import com.intellij.platform.icons.impl.modifiers.DefaultModifiersFactory
import com.intellij.platform.icons.impl.patchers.DefaultSvgPatcherDesigner
import com.intellij.platform.icons.modifiers.IconModifier
import com.intellij.platform.icons.modifiers.ModifiersFactory
import com.intellij.platform.icons.patchers.SvgPatcher
import com.intellij.platform.icons.scale.IconScale
import com.intellij.platform.icons.swing.ScalableSwingIcon
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.serialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.SerializersModuleBuilder

abstract class DefaultIconManager : IconManager {
    protected abstract val resolverService: DeferredIconResolverService

    private val deferredIconDeserializer by lazy { DefaultDeferredIconSerializer(this) }

    override fun deferredIcon(
        placeholder: Icon?,
        evaluator: suspend () -> Icon,
    ): Icon =
        resolverService.getOrCreateDeferredIcon(generateDeferredIconIdentifier(), placeholder) {
            id,
            ref ->
            createDeferredIconResolver(id, ref, evaluator)
        }

    internal fun registerDeserializedDeferredIcon(icon: DefaultDeferredIcon): DefaultDeferredIcon =
        resolverService.register(icon) { id, ref -> createDeferredIconResolver(id, ref, null) }

    protected open fun generateDeferredIconIdentifier(): IconIdentifier {
        return StringIconIdentifier("dynamicIcon_" + dynamicIconNextId.getAndIncrement().toString())
    }

    override suspend fun forceEvaluation(icon: DeferredIcon): Icon = resolverService.forceEvaluation(icon)

    fun scheduleEvaluation(icon: DeferredIcon) {
        resolverService.scheduleEvaluation(icon)
    }

    protected open fun createDeferredIconResolver(
        id: IconIdentifier,
        ref: WeakReference<DefaultDeferredIcon>,
        evaluator: (suspend () -> Icon)?,
    ): DeferredIconResolver {
        if (evaluator == null) error("Evaluator is not specified for icon $id")
        return InPlaceDeferredIconResolver(resolverService, id, ref, evaluator)
    }

    open fun SerializersModuleBuilder.buildCustomSerializers() {
        // Add nothing by default
    }

    abstract suspend fun sendDeferredNotifications(id: IconIdentifier, result: Icon)

    abstract fun markDeferredIconUnused(id: IconIdentifier)

    open fun getSerializersModule(): SerializersModule = SerializersModule {
        polymorphic(Icon::class, DefaultLayeredIcon::class, DefaultLayeredIcon.serializer())
        polymorphic(Icon::class, DefaultDeferredIcon::class, deferredIconDeserializer)
        polymorphic(DeferredIcon::class, DefaultDeferredIcon::class, deferredIconDeserializer)
        polymorphic(IconModifier::class, IconModifier.Companion::class, IconModifierConstSerializer)
        polymorphic(IconIdentifier::class, StringIconIdentifier::class, StringIconIdentifier.serializer())

        iconLayer(AnimatedIconLayer::class)
        iconLayer(NestedIconLayer::class)
        iconLayer(ImageIconLayer::class)
        iconLayer(LayoutIconLayer::class)
        iconLayer(ShapeIconLayer::class)

        buildCustomSerializers()
    }

    override fun toSwingIcon(icon: Icon, scale: IconScale): ScalableSwingIcon {
        error("Swing Icons are not supported.")
    }

    override fun toNewIcon(swingIcon: javax.swing.Icon): Icon {
        error("Swing Icons are not supported.")
    }

    override fun addSwingLayer(designer: IconDesigner, swingIcon: javax.swing.Icon, modifier: IconModifier) {
        error("Swing Icons are not supported.")
    }

    override fun svgPatcher(designer: SvgPatcherDesigner.() -> Unit): SvgPatcher {
        val designer = DefaultSvgPatcherDesigner()
        designer.designer()
        return designer.build()
    }

    override fun modifiersFactory(): ModifiersFactory = DefaultModifiersFactory

    override fun colorFilterFactory(): ColorFilterFactory = DefaultColorFilterFactory

    override fun unitsFactory(): UnitsFactory = DefaultUnitsFactory

    companion object {
        fun getDefaultManagerInstance(): DefaultIconManager =
            IconManager.getInstance() as? DefaultIconManager ?: error("IconManager is not DefaultIconManager.")

        private val dynamicIconNextId = AtomicInteger()
    }
}

private object IconModifierConstSerializer : KSerializer<IconModifier.Companion> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("IconModifier.Companion") { element("isEmpty", serialDescriptor<Boolean>()) }

    override fun serialize(encoder: Encoder, value: IconModifier.Companion) {
        encoder.encodeStructure(descriptor) { encodeBooleanElement(descriptor, 0, true) }
    }

    override fun deserialize(decoder: Decoder): IconModifier.Companion {
        var v: Boolean? = null
        decoder.decodeStructure(descriptor) {
            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    CompositeDecoder.DECODE_DONE -> break
                    0 -> v = decodeBooleanElement(descriptor, 0)
                    else -> error("Unexpected element index: $index")
                }
            }
        }
        require(v == true) { "Unexpected value: '$v'" }
        return IconModifier
    }
}

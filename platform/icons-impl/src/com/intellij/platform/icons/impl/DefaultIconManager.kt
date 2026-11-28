// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl

import com.intellij.platform.icons.DeferredIconDescriptor
import com.intellij.platform.icons.IconDescriptor
import com.intellij.platform.icons.IconIdentifier
import com.intellij.platform.icons.IconManager
import com.intellij.platform.icons.ImageResourceLocation
import com.intellij.platform.icons.design.Color
import com.intellij.platform.icons.design.IconAlign
import com.intellij.platform.icons.design.IconMargin
import com.intellij.platform.icons.design.IconUnit
import com.intellij.platform.icons.design.Shape
import com.intellij.platform.icons.impl.design.DefaultCircle
import com.intellij.platform.icons.impl.design.DefaultDisplayPoint
import com.intellij.platform.icons.impl.design.DefaultFactorScale
import com.intellij.platform.icons.impl.design.DefaultFillAreaScale
import com.intellij.platform.icons.impl.design.DefaultFitAreaScale
import com.intellij.platform.icons.impl.design.DefaultIconAlign
import com.intellij.platform.icons.impl.design.DefaultIconMargin
import com.intellij.platform.icons.impl.design.DefaultPixel
import com.intellij.platform.icons.impl.design.DefaultRectangle
import com.intellij.platform.icons.impl.design.DefaultSRGB
import com.intellij.platform.icons.impl.layers.SpacerIconLayer
import com.intellij.platform.icons.impl.modifiers.AlignIconModifier
import com.intellij.platform.icons.impl.modifiers.AlphaIconModifier
import com.intellij.platform.icons.impl.modifiers.ApplyableIconModifier
import com.intellij.platform.icons.impl.modifiers.ColorFilterModifier
import com.intellij.platform.icons.impl.modifiers.CombinedIconModifier
import com.intellij.platform.icons.impl.modifiers.CutoutMarginModifier
import com.intellij.platform.icons.impl.modifiers.MarginIconModifier
import com.intellij.platform.icons.impl.modifiers.RootIconModifier
import com.intellij.platform.icons.impl.modifiers.ScaleModifier
import com.intellij.platform.icons.impl.modifiers.SizeVariantModifier
import com.intellij.platform.icons.impl.modifiers.StrokeModifier
import com.intellij.platform.icons.impl.modifiers.SvgPatcherModifier
import kotlin.reflect.KClass
import com.intellij.platform.icons.design.IconDesigner
import com.intellij.platform.icons.design.SvgPatcherDesigner
import com.intellij.platform.icons.design.UnitsFactory
import com.intellij.platform.icons.filters.ColorFilter
import com.intellij.platform.icons.filters.ColorFilterFactory
import com.intellij.platform.icons.impl.filters.DefaultColorFilterFactory
import com.intellij.platform.icons.impl.filters.TintColorFilter
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
import javax.swing.Icon

abstract class DefaultIconManager : IconManager {
   protected abstract fun getResolverService(): DeferredIconResolverService

    private val deferredIconDeserializer by lazy { DefaultDeferredIconSerializer(this) }

    override fun deferredIconDescriptor(
      placeholder: IconDescriptor?,
      evaluator: suspend () -> IconDescriptor,
    ): IconDescriptor =
        getResolverService().getOrCreateDeferredIcon(generateDeferredIconIdentifier(), placeholder) {
            id,
            ref ->
            createDeferredIconResolver(id, ref, evaluator)
        }

    internal fun registerDeserializedDeferredIcon(icon: DefaultDeferredIconDescriptor): DefaultDeferredIconDescriptor =
      getResolverService().register(icon) { id, ref -> createDeferredIconResolver(id, ref, null) }

    protected open fun generateDeferredIconIdentifier(): IconIdentifier {
        return StringIconIdentifier("dynamicIcon_" + dynamicIconNextId.getAndIncrement().toString())
    }

    override suspend fun forceEvaluation(icon: DeferredIconDescriptor): IconDescriptor = getResolverService().forceEvaluation(icon)

    fun scheduleEvaluation(icon: DeferredIconDescriptor) {
        getResolverService().scheduleEvaluation(icon)
    }

    protected open fun createDeferredIconResolver(
      id: IconIdentifier,
      ref: WeakReference<DefaultDeferredIconDescriptor>,
      evaluator: (suspend () -> IconDescriptor)?,
    ): DeferredIconResolver {
        if (evaluator == null) error("Evaluator is not specified for icon $id")
        return InPlaceDeferredIconResolver(getResolverService(), id, ref, evaluator)
    }

    open fun SerializersModuleBuilder.buildCustomSerializers() {
        // Add nothing by default
    }

    abstract suspend fun sendDeferredNotifications(id: IconIdentifier, result: IconDescriptor)

    abstract fun markDeferredIconUnused(id: IconIdentifier)

    open fun getSerializersModule(): SerializersModule = SerializersModule {
        polymorphic(IconDescriptor::class, DefaultLayeredIconDescriptor::class, DefaultLayeredIconDescriptor.serializer())
        polymorphic(IconDescriptor::class, DefaultDeferredIconDescriptor::class, deferredIconDeserializer)
        polymorphic(DeferredIconDescriptor::class, DefaultDeferredIconDescriptor::class, deferredIconDeserializer)
        polymorphic(IconModifier::class, IconModifier.Companion::class, IconModifierConstSerializer)
        polymorphic(IconIdentifier::class, StringIconIdentifier::class, StringIconIdentifier.serializer())
        polymorphic(ImageResourceLocation::class, UrlImageResourceLocation::class, UrlImageResourceLocation.serializer())
        polymorphic(ImageResourceLocation::class, FileImageResourceLocation::class, FileImageResourceLocation.serializer())

        iconLayer(AnimatedIconLayer::class)
        iconLayer(NestedIconLayer::class)
        iconLayer(ImageIconLayer::class)
        iconLayer(LayoutIconLayer::class)
        iconLayer(ShapeIconLayer::class)
        iconLayer(SpacerIconLayer::class)

        iconModifier(AlignIconModifier::class, AlignIconModifier.serializer())
        iconModifier(AlphaIconModifier::class, AlphaIconModifier.serializer())
        iconModifier(ColorFilterModifier::class, ColorFilterModifier.serializer())
        iconModifier(CombinedIconModifier::class, CombinedIconModifier.serializer())
        iconModifier(CutoutMarginModifier::class, CutoutMarginModifier.serializer())
        iconModifier(MarginIconModifier::class, MarginIconModifier.serializer())
        iconModifier(RootIconModifier::class, RootIconModifier.serializer())
        iconModifier(ScaleModifier::class, ScaleModifier.serializer())
        iconModifier(SizeVariantModifier::class, SizeVariantModifier.serializer())
        iconModifier(StrokeModifier::class, StrokeModifier.serializer())
        iconModifier(SvgPatcherModifier::class, SvgPatcherModifier.serializer())

        polymorphic(IconScale::class, DefaultFactorScale::class, DefaultFactorScale.serializer())
        polymorphic(IconScale::class, DefaultFitAreaScale::class, DefaultFitAreaScale.serializer())
        polymorphic(IconScale::class, DefaultFillAreaScale::class, DefaultFillAreaScale.serializer())
        polymorphic(IconUnit::class, DefaultPixel::class, DefaultPixel.serializer())
        polymorphic(IconUnit::class, DefaultDisplayPoint::class, DefaultDisplayPoint.serializer())
        polymorphic(Color::class, DefaultSRGB::class, DefaultSRGB.serializer())
        polymorphic(Shape::class, DefaultRectangle::class, DefaultRectangle.serializer())
        polymorphic(Shape::class, DefaultCircle::class, DefaultCircle.serializer())
        polymorphic(IconAlign::class, DefaultIconAlign::class, DefaultIconAlign.serializer())
        polymorphic(IconMargin::class, DefaultIconMargin::class, DefaultIconMargin.serializer())
        polymorphic(ColorFilter::class, TintColorFilter::class, TintColorFilter.serializer())

        buildCustomSerializers()
    }

    override fun createSwingIcon(iconDescriptor: IconDescriptor, scale: IconScale): ScalableSwingIcon {
        error("Swing Icons are not supported.")
    }

    override fun toIconDescriptor(swingIcon: Icon): IconDescriptor {
        error("Swing Icons are not supported.")
    }

    override fun addSwingLayer(designer: IconDesigner, swingIcon: Icon, modifier: IconModifier) {
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


/**
 * Registers the modifier [klass] as an [IconModifier], the type of a layer modifier, and as an [ApplyableIconModifier],
 * the type of the parts of a [CombinedIconModifier].
 */
private fun <T : ApplyableIconModifier> SerializersModuleBuilder.iconModifier(klass: KClass<T>, serializer: KSerializer<T>) {
    polymorphic(IconModifier::class, klass, serializer)
    polymorphic(ApplyableIconModifier::class, klass, serializer)
}

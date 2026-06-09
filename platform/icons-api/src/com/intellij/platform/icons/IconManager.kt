// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons

import com.intellij.platform.icons.design.IconDesigner
import com.intellij.platform.icons.design.SvgPatcherDesigner
import com.intellij.platform.icons.design.UnitsFactory
import com.intellij.platform.icons.filters.ColorFilterFactory
import com.intellij.platform.icons.modifiers.IconModifier
import com.intellij.platform.icons.modifiers.ModifiersFactory
import com.intellij.platform.icons.patchers.SvgPatcher
import com.intellij.platform.icons.scale.IconScale
import com.intellij.platform.icons.scale.factor
import com.intellij.platform.icons.swing.ScalableSwingIcon
import java.util.ServiceLoader
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface IconManager {
    /**
     * Creates new Icon "description", this is a cheap operation, to render the Icon, use Icon.createRenderer()
     * function. Use convenience top-level method icon {} instead if possible.
     */
    fun icon(designer: IconDesigner.() -> Unit): Icon

    /** @see com.intellij.platform.icons.deferredIcon */
    fun deferredIcon(
        placeholder: Icon?,
        evaluator: suspend () -> Icon,
    ): Icon

    suspend fun forceEvaluation(icon: DeferredIcon): Icon

    /**
     * Converts specific Icon to swing Icon. ! This is an expensive operation and can include image loading, reuse the
     * instance if possible. !
     */
    fun toSwingIcon(icon: Icon, scale: IconScale = factor(1f)): ScalableSwingIcon

    fun addSwingLayer(designer: IconDesigner, swingIcon: javax.swing.Icon, modifier: IconModifier)

    fun toNewIcon(swingIcon: javax.swing.Icon): Icon

    fun svgPatcher(designer: SvgPatcherDesigner.() -> Unit): SvgPatcher

    @ApiStatus.Internal fun modifiersFactory(): ModifiersFactory

    @ApiStatus.Internal fun colorFilterFactory(): ColorFilterFactory

    @ApiStatus.Internal fun unitsFactory(): UnitsFactory

    companion object {
        @Volatile private var instance: IconManager? = null

        @JvmStatic fun getInstance(): IconManager = instance ?: loadFromSPI()

        private fun loadFromSPI(): IconManager =
            ServiceLoader.load(IconManager::class.java).firstOrNull()
                ?: error("IconManager instance is not set and there is no SPI service on classpath.")

        fun activate(manager: IconManager) {
            instance = manager
        }

        @ApiStatus.Internal fun modifiers(): ModifiersFactory = getInstance().modifiersFactory()

        @ApiStatus.Internal fun colorFilters(): ColorFilterFactory = getInstance().colorFilterFactory()

        @ApiStatus.Internal fun units(): UnitsFactory = getInstance().unitsFactory()
    }
}

/**
 * Creates new Icon. The result should be serializable and contains description of what the Icon should look like.
 *
 * To render the Icon, renderer has to be obtained first using createRenderer(), however this should be only done from
 * inside components. (check intellij.platform.icons.api.rendering module), for usage inside swing, use toSwingIcon
 * method.
 *
 * Usage: ''' icon { image("icons/icon.svg", MyClass::class.java.classLoader) } '''
 *
 * Check the designer interface for layer options. Also check intellij.platform.icons.api.swing module to find out how
 * to convert swing icons and new icons.
 *
 * @see IconManager.toSwingIcon
 */
fun icon(designer: IconDesigner.() -> Unit): Icon = IconManager.getInstance().icon(designer)

/**
 * Deferred icon allows apis to return an Icon that takes some time to compute; optional placeholder can be included to
 * allow rendering it before the actual icon is ready.
 */
fun deferredIcon(
    placeholder: Icon?,
    evaluator: suspend () -> Icon,
): Icon = IconManager.getInstance().deferredIcon(placeholder, evaluator)

fun imageIcon(path: String, classLoader: ClassLoader? = null, modifier: IconModifier = IconModifier): Icon = icon {
    image(path, classLoader, modifier)
}

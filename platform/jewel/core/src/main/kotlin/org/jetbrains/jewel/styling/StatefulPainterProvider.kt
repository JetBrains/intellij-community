package org.jetbrains.jewel.styling

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.ResourceLoader

@Immutable
interface StatefulPainterProvider<T> {

    // TODO apply color overrides to the painter

    /**
     * Obtain a painter for the provided [state].
     *
     * A [resourceLoader] that allows loading the corresponding resource
     * must be loading. For example, if your resource is in module `my-module`'s
     * resources, the [resourceLoader] must be pointing to `my-module`s
     * classloader.
     *
     * Passing the wrong [ResourceLoader] will cause your resources not to load,
     * and you will get cryptic errors. Please also note that using
     * [ResourceLoader.Default] will probably cause loading to fail if you are
     * trying to load the icons from a different module. For example, if Jewel
     * is running in the IDE and you use [ResourceLoader.Default] to try and
     * load a default IDE resource, it will fail.
     */
    @Composable
    fun getPainter(state: T, resourceLoader: ResourceLoader): State<Painter>
}

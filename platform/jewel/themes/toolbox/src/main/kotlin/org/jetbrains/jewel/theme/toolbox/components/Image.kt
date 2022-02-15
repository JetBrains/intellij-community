package org.jetbrains.jewel.theme.toolbox.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.jewel.components.ImageSlice
import org.jetbrains.jewel.components.ImageSlicePainter
import org.jetbrains.jewel.components.ImageSliceValues

@Composable
fun Image(
    image: ImageBitmap,
    slices: ImageSliceValues,
    scale: Float = 1.0f,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Fit,
    alpha: Float = DefaultAlpha,
    colorFilter: ColorFilter? = null
) = Image(ImageSlice(image, slices), scale, modifier, alignment, contentScale, alpha, colorFilter)

@Composable
fun Image(
    imageSlice: ImageSlice,
    scale: Float = 1.0f,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Fit,
    alpha: Float = DefaultAlpha,
    colorFilter: ColorFilter? = null
) {
    val imagePainter = remember(imageSlice) { ImageSlicePainter(imageSlice, scale) }
    Image(
        painter = imagePainter,
        contentDescription = "",
        modifier = modifier,
        alignment = alignment,
        contentScale = contentScale,
        alpha = alpha,
        colorFilter = colorFilter
    )
}

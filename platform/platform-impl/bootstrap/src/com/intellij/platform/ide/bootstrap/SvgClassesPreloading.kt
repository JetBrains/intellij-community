// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.bootstrap

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.openapi.diagnostic.logger
import com.intellij.ui.svg.createJSvgDocument
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting

/**
 * Parses [PRELOADING_SVG], which has the elements and attributes that the IDE icons use most.
 * The XML and SVG parser classes load on the calling thread, so the EDT does not load them with the first icon.
 * The parsing creates no image and touches no Java2D surface.
 *
 * @return the class loader of the SVG classes, for [preloadSvgRendererClasses]
 */
internal fun preloadSvgParserClasses(): ClassLoader {
  return createJSvgDocument(PRELOADING_SVG.encodeToByteArray()).javaClass.classLoader
}

/**
 * Loads [SVG_RENDERER_CLASS_NAMES] from [classLoader] without initialization, so the EDT does not load them with the first icon.
 * The loading renders nothing, so it touches no Java2D class and may run while the EDT creates the splash.
 * The EDT still initializes the classes and links their lambdas with the first icon. That reads nothing from the jar files.
 */
internal fun preloadSvgRendererClasses(classLoader: ClassLoader) {
  for (className in SVG_RENDERER_CLASS_NAMES) {
    try {
      Class.forName(className, false, classLoader)
    }
    catch (e: ClassNotFoundException) {
      logger<AppStarter>().debug("Cannot preload $className: $e")
    }
    catch (e: Throwable) {
      rethrowControlFlowException(e)
      logger<AppStarter>().warn("Cannot preload $className", e)
    }
  }
}

/**
 * The classes that the rendering of [PRELOADING_SVG] loads after [preloadSvgParserClasses].
 * The lambda classes have no name, so they are not in the set.
 */
@VisibleForTesting
@ApiStatus.Internal
val SVG_RENDERER_CLASS_NAMES: Set<String> = setOf(
  "com.intellij.ui.svg.SvgKt",
  "com.intellij.ui.svg.SvgKt\$colorPatchCache\$1\$1",
  "com.intellij.ui.svg.JsvgKt",
  "com.intellij.ui.svg.AdaptiveImageLoadKt",
  "com.intellij.ui.svg.ImageDimension",
  "com.intellij.ui.svg.ImageDimension\$Unit",
  "com.intellij.ui.svg.JsvgKt\$WhenMappings",
  "com.github.weisj.jsvg.renderer.NullPlatformSupport",
  "com.github.weisj.jsvg.renderer.output.TextOutput",
  "com.github.weisj.jsvg.renderer.output.impl.RenderingHintsUtil",
  "com.github.weisj.jsvg.renderer.SVGRenderingHints",
  "com.github.weisj.jsvg.renderer.SVGRenderingHints\$Key",
  "com.github.weisj.jsvg.renderer.SVGRenderingHints\$Value",
  "com.github.weisj.jsvg.renderer.output.impl.CurrentColorProvider",
  "com.github.weisj.jsvg.renderer.output.impl.Graphics2DOutput",
  "com.github.weisj.jsvg.renderer.output.Output\$SafeState",
  "com.github.weisj.jsvg.renderer.output.impl.GraphicsUtil",
  "com.github.weisj.jsvg.renderer.output.impl.GraphicsUtil\$WrappingPaint",
  "com.github.weisj.jsvg.renderer.impl.context.RenderContextAccessor",
  "com.github.weisj.jsvg.renderer.impl.context.RenderContextAccessor\$Accessor",
  "com.github.weisj.jsvg.renderer.RenderContext\$1",
  "com.github.weisj.jsvg.attributes.font.MeasurableFontSpec",
  "com.github.weisj.jsvg.attributes.font.FontStyle\$Normal",
  "com.github.weisj.jsvg.attributes.font.FontStyle\$Italic",
  "com.github.weisj.jsvg.renderer.impl.NodeRenderer",
  "com.github.weisj.jsvg.renderer.impl.Info",
  "com.github.weisj.jsvg.renderer.impl.Info\$InfoWithIsolation",
  "com.github.weisj.jsvg.renderer.RenderContext\$EstablishRootMeasure",
  "com.github.weisj.jsvg.animation.value.AnimatedColor",
  "com.github.weisj.jsvg.renderer.impl.ElementBounds",
  "com.github.weisj.jsvg.renderer.impl.IsolationEffects",
  "com.github.weisj.jsvg.nodes.prototype.HasShape\$Box",
  "com.github.weisj.jsvg.nodes.ShapeNode\$1",
  "com.github.weisj.jsvg.renderer.impl.ElementBounds\$1",
  "com.github.weisj.jsvg.attributes.stroke.StrokeResolver",
  "com.github.weisj.jsvg.renderer.impl.ShapeRenderer\$ShapePaintContext",
  "com.github.weisj.jsvg.geometry.util.GeometryUtil",
  "com.github.weisj.jsvg.renderer.impl.ShapeRenderer\$PaintShape",
  "com.github.weisj.jsvg.renderer.impl.ShapeRenderer\$ShapeMarkerInfo",
  "com.github.weisj.jsvg.renderer.impl.ShapeRenderer",
  "com.github.weisj.jsvg.renderer.output.impl.GraphicsResetHelper",
  "com.github.weisj.jsvg.renderer.impl.ShapeRenderer\$1",
  "com.github.weisj.jsvg.renderer.impl.ShapeRenderer\$PaintWithOpacity",
  "com.github.weisj.jsvg.renderer.impl.PaintResolver",
  "com.github.weisj.jsvg.renderer.output.impl.GraphicsUtil\$DisposablePaint",
  "com.github.weisj.jsvg.paint.impl.MaskedPaint",
  "com.github.weisj.jsvg.util.BlittableImage",
  "com.github.weisj.jsvg.geometry.util.GeometryUtil\$Space",
  "com.github.weisj.jsvg.renderer.impl.context.ContextElementAttributes",
  "com.github.weisj.jsvg.nodes.prototype.Transformable\$1",
  "com.github.weisj.jsvg.paint.impl.MaskedPaint\$MaskPaintContext",
  "com.github.weisj.jsvg.util.ColorUtil",
)

/**
 * The elements of three platform icons in `community/platform/icons/src/expui`, without a change:
 * `image/colorPicker.svg`, `toolwindows/palette@20x20.svg`, `vcs/showUnversionedFiles.svg`.
 *
 * Rare features, such as `opacity`, a gradient, `<ellipse>` or `<polygon>`, are not in the document.
 * The EDT loads their classes with the first icon that has them.
 */
@VisibleForTesting
@ApiStatus.Internal
const val PRELOADING_SVG: String = """<svg width="16" height="16" viewBox="0 0 16 16" fill="none" xmlns="http://www.w3.org/2000/svg">
  <g clip-path="url(#clip0_3769_2012)">
    <path d="M1.75 12.75v5a.5.5 0 0 0 .5.5h15.5a.5.5 0 0 0 .5-.5v-5a.5.5 0 0 0-.5-.5H2.25a.5.5 0 0 0-.5.5z" stroke="#6C707E"
          stroke-width="1.5"/>
    <circle cx="15" cy="15.25" r="1" fill="#6C707E"/>
    <path fill-rule="evenodd" clip-rule="evenodd"
          d="M3.674 6.856l3.182-3.182L13.182 10h2.121L7.74 2.436a1.25 1.25 0 0 0-1.768 0L2.436 5.972a1.25 1.25 0 0 0 0 1.768L4.696 10h2.122L3.674 6.856zM13 2.75h4.5V10h1.329a.5.5 0 0 1 .171.03V2.5c0-.69-.56-1.25-1.25-1.25h-5c-.69 0-1.25.56-1.25 1.25v1.656l1.5 1.5V2.75z"
          fill="#6C707E"/>
  </g>
  <defs>
    <clipPath id="clip0_3769_2012">
      <path fill="#fff" d="M0 0h20v20H0z"/>
    </clipPath>
  </defs>
  <defs><mask id="cutout-mask"><rect width="16" height="16" fill="white"/><g stroke="black" stroke-width="3" stroke-linejoin="round" stroke-linecap="round"><path transform="matrix(.727 0 0 .727 6.36 6.05)" d="m8 2c-3 0-3 2.91-3 3h1s0-2 2-2c1.16 0 2 0.743 2 2 0 2.29-3 1.06-3 5h1c0-3.38 3-2 3-5 0-1.42-1-3-3-3zm-1 10h1v1h-1z" fill="black" stroke="black"/></g></mask></defs><g mask="url(#cutout-mask)"><path fill-rule="evenodd" clip-rule="evenodd" d="M11 1H7.91421C7.51639 1 7.13486 1.15803 6.85355 1.43934L3.43934 4.85355C3.15804 5.13486 3 5.51639 3 5.91421V13C3 14.1046 3.89543 15 5 15H11C12.1046 15 13 14.1046 13 13V3C13 1.89543 12.1046 1 11 1ZM4 13C4 13.5523 4.44772 14 5 14H11C11.5523 14 12 13.5523 12 13V3C12 2.44772 11.5523 2 11 2H8V4.5C8 5.32843 7.32843 6 6.5 6H4V13ZM4.70711 5L7 2.70711V4.5C7 4.77614 6.77614 5 6.5 5H4.70711Z" fill="#6C707E"/><path d="M5 14C4.44772 14 4 13.5523 4 13V6H6.5C7.32843 6 8 5.32843 8 4.5V2H11C11.5523 2 12 2.44772 12 3V13C12 13.5523 11.5523 14 11 14H5Z" fill="#EBECF0"/><path d="M7 2.70711L4.70711 5H6.5C6.77614 5 7 4.77614 7 4.5V2.70711Z" fill="#EBECF0"/></g><g><path transform="matrix(.727 0 0 .727 6.36 6.05)" d="m8 2c-3 0-3 2.91-3 3h1s0-2 2-2c1.16 0 2 0.743 2 2 0 2.29-3 1.06-3 5h1c0-3.38 3-2 3-5 0-1.42-1-3-3-3zm-1 10h1v1h-1z" fill="#6C707E"/></g>
  <path d="M11.9506 7.80135L14.0415 5.70713C14.4586 5.3007 14.4535 4.64266 14.048 4.23291L12.3274 2.51273C11.9173 2.10344 11.2952 2.03991 10.8863 2.45743L8.73993 4.5907M11.9506 7.80135L8.73993 4.5907M11.9506 7.80135L6.80074 12.9395L3.04636 14.0803C2.66307 14.1968 2.30529 13.8377 2.42312 13.4548L3.56033 9.75954L8.73993 4.5907" stroke="#6C707E" stroke-miterlimit="10"/>
  <rect x="7.53125" y="2.61719" width="9.01369" height="1" rx="0.5" transform="rotate(45 7.53125 2.61719)" fill="#6C707E"/>
</svg>"""

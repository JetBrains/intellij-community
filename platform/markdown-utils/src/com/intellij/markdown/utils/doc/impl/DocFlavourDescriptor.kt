// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.utils.doc.impl

import com.intellij.openapi.project.Project
import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.flavours.commonmark.CommonMarkMarkerProcessor
import org.intellij.markdown.flavours.gfm.GFMConstraints
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.html.GeneratingProvider
import org.intellij.markdown.parser.LinkMap
import org.intellij.markdown.parser.MarkerProcessor
import org.intellij.markdown.parser.MarkerProcessorFactory
import org.intellij.markdown.parser.ProductionHolder
import org.intellij.markdown.parser.constraints.CommonMarkdownConstraints
import org.intellij.markdown.parser.markerblocks.MarkerBlockProvider
import org.intellij.markdown.parser.markerblocks.providers.HtmlBlockProvider
import java.net.URI

internal class DocFlavourDescriptor(private val project: Project?) : GFMFlavourDescriptor() {
  override val markerProcessorFactory: MarkerProcessorFactory
    get() = object : MarkerProcessorFactory {
      override fun createMarkerProcessor(productionHolder: ProductionHolder): MarkerProcessor<*> =
        DocumentationMarkerProcessor(productionHolder, GFMConstraints.BASE)
    }

  override fun createHtmlGeneratingProviders(linkMap: LinkMap, baseURI: URI?): Map<IElementType, GeneratingProvider> {
    val result = HashMap(super.createHtmlGeneratingProviders(linkMap, baseURI))
    result[MarkdownTokenTypes.HTML_TAG] = DocSanitizingTagGeneratingProvider()
    result[MarkdownElementTypes.CODE_FENCE] = DocCodeFenceGeneratingProvider(project)
    result[MarkdownElementTypes.CODE_SPAN] = DocCodeSpanGeneratingProvider()
    return result
  }

  private class DocumentationMarkerProcessor(productionHolder: ProductionHolder,
                                             constraintsBase: CommonMarkdownConstraints) : CommonMarkMarkerProcessor(productionHolder,
                                                                                                                     constraintsBase) {
    override fun getMarkerBlockProviders(): List<MarkerBlockProvider<StateInfo>> =
      super.getMarkerBlockProviders().filter { it !is HtmlBlockProvider } + DocHtmlBlockProvider
  }
}

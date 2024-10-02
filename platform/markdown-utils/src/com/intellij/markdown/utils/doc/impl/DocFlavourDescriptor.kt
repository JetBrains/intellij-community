// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.utils.doc.impl

import com.intellij.lang.Language
import com.intellij.openapi.project.Project
import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.flavours.commonmark.CommonMarkMarkerProcessor
import org.intellij.markdown.flavours.gfm.GFMConstraints
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.html.GeneratingProvider
import org.intellij.markdown.html.ImageGeneratingProvider
import org.intellij.markdown.html.ReferenceLinksGeneratingProvider
import org.intellij.markdown.parser.LinkMap
import org.intellij.markdown.parser.MarkerProcessor
import org.intellij.markdown.parser.MarkerProcessorFactory
import org.intellij.markdown.parser.ProductionHolder
import org.intellij.markdown.parser.constraints.CommonMarkdownConstraints
import org.intellij.markdown.parser.markerblocks.MarkerBlockProvider
import org.intellij.markdown.parser.markerblocks.providers.HtmlBlockProvider
import java.net.URI

private val baseHtmlGeneratingProvidersMap =
  GFMFlavourDescriptor().createHtmlGeneratingProviders(LinkMap(emptyMap()), null) + hashMapOf(
    MarkdownTokenTypes.HTML_TAG to DocSanitizingTagGeneratingProvider(),
    MarkdownElementTypes.PARAGRAPH to DocParagraphGeneratingProvider(),
  )


internal class DocFlavourDescriptor(private val project: Project, private val defaultLanguage: Language?) : GFMFlavourDescriptor() {
  override val markerProcessorFactory: MarkerProcessorFactory
    get() = object : MarkerProcessorFactory {
      override fun createMarkerProcessor(productionHolder: ProductionHolder): MarkerProcessor<*> =
        DocumentationMarkerProcessor(productionHolder, GFMConstraints.BASE)
    }

  override fun createHtmlGeneratingProviders(linkMap: LinkMap, baseURI: URI?): Map<IElementType, GeneratingProvider> =
    MergedMap(hashMapOf(MarkdownElementTypes.FULL_REFERENCE_LINK to
                          ReferenceLinksGeneratingProvider(linkMap, baseURI, absolutizeAnchorLinks).makeXssSafe(useSafeLinks),
                        MarkdownElementTypes.SHORT_REFERENCE_LINK to
                          ReferenceLinksGeneratingProvider(linkMap, baseURI, absolutizeAnchorLinks).makeXssSafe(useSafeLinks),
                        MarkdownElementTypes.IMAGE to ImageGeneratingProvider(linkMap, baseURI).makeXssSafe(useSafeLinks),

                        MarkdownElementTypes.CODE_BLOCK to DocCodeBlockGeneratingProvider(project, defaultLanguage),
                        MarkdownElementTypes.CODE_FENCE to DocCodeBlockGeneratingProvider(project, defaultLanguage),
                        MarkdownElementTypes.CODE_SPAN to DocCodeSpanGeneratingProvider(project, defaultLanguage)),
              baseHtmlGeneratingProvidersMap)

  private class DocumentationMarkerProcessor(productionHolder: ProductionHolder,
                                             constraintsBase: CommonMarkdownConstraints) : CommonMarkMarkerProcessor(productionHolder,
                                                                                                                     constraintsBase) {
    override fun getMarkerBlockProviders(): List<MarkerBlockProvider<StateInfo>> =
      super.getMarkerBlockProviders().filter { it !is HtmlBlockProvider } + DocHtmlBlockProvider
  }

  private class MergedMap(val map1: Map<IElementType, GeneratingProvider>,
                          val map2: Map<IElementType, GeneratingProvider>) : Map<IElementType, GeneratingProvider> {

    override fun isEmpty(): Boolean =
      map1.isEmpty() && map2.isEmpty()

    override fun get(key: IElementType): GeneratingProvider? =
      map1[key] ?: map2[key]

    override fun containsValue(value: GeneratingProvider): Boolean =
      map1.containsValue(value) || map2.containsValue(value)

    override fun containsKey(key: IElementType): Boolean =
      map1.containsKey(key) || map2.containsKey(key)

    override val entries: Set<Map.Entry<IElementType, GeneratingProvider>>
      get() = throw UnsupportedOperationException()
    override val keys: Set<IElementType>
      get() = throw UnsupportedOperationException()
    override val size: Int
      get() = throw UnsupportedOperationException()
    override val values: Collection<GeneratingProvider>
      get() = throw UnsupportedOperationException()

  }
}

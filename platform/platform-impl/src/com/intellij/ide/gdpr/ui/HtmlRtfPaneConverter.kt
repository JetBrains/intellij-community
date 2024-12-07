// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.gdpr.ui

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import java.awt.Cursor
import java.awt.Desktop
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.awt.event.MouseMotionListener
import java.net.URI
import java.util.Locale
import javax.swing.JTextPane
import javax.swing.text.DefaultStyledDocument

/**
 * This class creates a JTextPane with HTML content rendered by RTF. Only few HTML tags are supported.
 */
class HtmlRtfPane {

    private val linkMap: MutableMap<IntRange, String> = mutableMapOf()
    private val resultPane = JTextPane()

    private val mouseMotionListenersList = mutableListOf<MouseMotionListener>()
    private val mouseListenersList = mutableListOf<MouseListener>()

    fun create(htmlText: String): JTextPane {
        process(htmlText)
        return resultPane
    }

    fun replaceText(newText: String): JTextPane {
        resultPane.document.remove(0, resultPane.document.length)
        clearLinks()
        process(newText)
        return resultPane
    }

    private fun clearLinks() {
        mouseListenersList.forEach { resultPane.removeMouseListener(it) }
        mouseListenersList.clear()
        mouseMotionListenersList.forEach { resultPane.removeMouseMotionListener(it) }
        mouseMotionListenersList.clear()
        linkMap.clear()
    }

    private fun process(htmlContent: String): DefaultStyledDocument {
        val document = Jsoup.parse(htmlContent, "UTF-8")
        val licenseContentNode = document.getElementsByAttributeValue("class", "licenseContent").firstOrNull()
                                         ?: document.body()

        val styledDocument = resultPane.document as DefaultStyledDocument
        licenseContentNode.children().forEach {
            val style = when (it.tagName().uppercase(Locale.getDefault())) {
                "H1" -> Styles.H1
                "H2" -> Styles.H2
                "P" -> Styles.PARAGRAPH
                else -> Styles.REGULAR
            }
            val start = styledDocument.length
            styledDocument.insertString(styledDocument.length, it.text() + "\n", style)
            styledDocument.setParagraphAttributes(start, it.text().length + 1, style, false)
            if (it.tagName().uppercase(Locale.getDefault()) == "P") styleNodes(it, styledDocument, start, linkMap)
        }
        addHyperlinksListeners()
        return styledDocument
    }

    private fun addHyperlinksListeners() {
        val cursorListener = object : MouseAdapter() {
            override fun mouseMoved(e: MouseEvent?) {
                val location = e?.point?.location ?: return
                if (linkMap.keys.any { it.contains(resultPane.viewToModel(location)) })
                    resultPane.cursor = Cursor(Cursor.HAND_CURSOR)
                else
                    resultPane.cursor = Cursor(Cursor.DEFAULT_CURSOR)
            }
        }
        val linkListener = object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent?) {
                if (e?.button == MouseEvent.BUTTON1) {
                    val location = e.point.location
                    val range = linkMap.keys.firstOrNull { it.contains(resultPane.viewToModel(location)) } ?: return
                    val link = linkMap[range] ?: return
                    if (Desktop.isDesktopSupported()) {
                        Desktop.getDesktop().browse(URI(link))
                    }
                }
            }
        }
        resultPane.addMouseMotionListener(cursorListener)
        mouseMotionListenersList.add(cursorListener)
        resultPane.addMouseListener(linkListener)
        mouseListenersList.add(linkListener)
    }

    private fun styleNodes(nodeElement: Element,
                           styledDocument: DefaultStyledDocument,
                           offsetInDocument: Int,
                           linkMap: MutableMap<IntRange, String>) {
        var currentOffset = offsetInDocument
        val style = when (nodeElement.tagName().uppercase(Locale.getDefault())) {
            "STRONG" -> Styles.BOLD
            "A" -> {
                val linkUrl = nodeElement.attr("href")
                linkMap[IntRange(currentOffset, currentOffset + nodeElement.text().length + 1)] = linkUrl
                Styles.LINK
            }
            "HINT" -> Styles.HINT
            //"SUP" -> Styles.SUP don't use superscript due to IDEA-235457
            "SUP" -> null
            else -> Styles.REGULAR
        }
        if (style != null) {
            styledDocument.setCharacterAttributes(offsetInDocument, nodeElement.text().length, style, false)
        }
        if (nodeElement.childNodes().isEmpty()) return
        nodeElement.childNodes().forEach {
            if (it is TextNode) {
                //if first node starts with spaces.
                val length: Int = if (it.siblingIndex() == 0) {
                    (nodeElement.textNodes()[0] as TextNode).text().trimIndent().length
                } else {
                    it.text().length
                }
                currentOffset += length
            }
            if (it is Element) {
              styleNodes(it, styledDocument, currentOffset, linkMap)
              currentOffset += it.text().length
            }
        }
    }

}

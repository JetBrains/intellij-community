// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util

import com.intellij.codeEditor.printing.HTMLTextPainter
import com.intellij.codeEditor.printing.HtmlStyleManager
import com.intellij.concurrency.IdeaForkJoinWorkerThreadFactory
import com.intellij.idea.CommandLineApplication
import com.intellij.lang.Language
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.ProjectManager
import com.intellij.psi.PsiFileFactory
import com.intellij.testFramework.PlatformTestCase
import com.intellij.util.io.inputStream
import com.intellij.util.io.outputStream
import com.vladsch.flexmark.ast.FencedCodeBlock
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.html.HtmlWriter
import com.vladsch.flexmark.html.renderer.CoreNodeRenderer
import com.vladsch.flexmark.html.renderer.NodeRendererContext
import com.vladsch.flexmark.html.renderer.NodeRenderingHandler
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.options.DataHolder
import com.vladsch.flexmark.util.options.MutableDataSet
import java.io.StringWriter
import java.nio.file.Paths

private class MyApp : CommandLineApplication(true, false, true)

fun main(args: Array<String>) {
  PlatformTestCase.doAutodetectPlatformPrefix()
  MyApp()
  ApplicationManagerEx.getApplicationEx().load(null)
  IdeaForkJoinWorkerThreadFactory.setupForkJoinCommonPool(true)

  ProjectManager.getInstance().defaultProject

  val options = MutableDataSet()
  val htmlStyleManager = HtmlStyleManager(useInline = true)
  val parser = Parser.builder(options).build()
  val renderer = HtmlRenderer.builder(options)
    .nodeRendererFactory { IntelliJNodeRenderer(options, htmlStyleManager) }
    .build()

  val file = Paths.get("community/platform/util/src/com/intellij/util/xmlb/annotations/readme.md")
  val document = file.inputStream().reader().use { parser.parseReader(it) }

  // see generatePackageHtmlJavaDoc - head.style cannot be used to define styles, so, we use inline style
  val output = Paths.get("community/platform/util/src/com/intellij/util/xmlb/annotations/package.html")
  output.outputStream().bufferedWriter().use { writer ->
    renderer.render(document, writer)
    //writer.append("<html>\n<head>\n")
    //htmlStyleManager.writeStyleTag(writer, isUseLineNumberStyle = false)
    //writer.append("</head>\n<body>\n")
    //writer.write(data)
    //writer.append("</body>\n</html>")
  }

  System.exit(0)
}

private class IntelliJNodeRenderer(options: DataHolder, private val htmlStyleManager: HtmlStyleManager) : CoreNodeRenderer(options) {
  override fun getNodeRenderingHandlers(): MutableSet<NodeRenderingHandler<*>> {
    val set = LinkedHashSet<NodeRenderingHandler<*>>()
    set.add(NodeRenderingHandler(FencedCodeBlock::class.java) { node, context, html -> renderCode(node, context, html) })
    set.addAll(super.getNodeRenderingHandlers().filter { it.nodeType != FencedCodeBlock::class.java })
    return set
  }

  fun renderCode(node: FencedCodeBlock, context: NodeRendererContext, html: HtmlWriter) {
    html.line()
    //  html.srcPosWithTrailingEOL(node.chars).withAttr().tag("pre").openPre()
    //  html.srcPosWithEOL(node.contentChars).withAttr(CODE_CONTENT).tag("code")

    val writer = StringWriter()
    val project = ProjectManager.getInstance().defaultProject
    val psiFileFactory = PsiFileFactory.getInstance(project)
    runReadAction {
      val psiFile = psiFileFactory.createFileFromText(getLanguage(node), node.contentChars.normalizeEOL())
      val htmlTextPainter = HTMLTextPainter(psiFile, project, htmlStyleManager, false)

      writer.use {
        htmlTextPainter.paint(null, writer, false)
      }
    }

    html.rawIndentedPre(writer.buffer)

    //  html.tag("/code")
    //  html.tag("/pre").closePre()
    html.lineIf(context.htmlOptions.htmlBlockCloseTagEol)
  }
}

fun getLanguage(node: FencedCodeBlock): Language {
  val info = node.info
  if (info.isNotNull && !info.isBlank) run {
    val space = info.indexOf(' ')
    val language = if (space == -1) info else info.subSequence(0, space)
    return Language.findLanguageByID(language.unescape().toUpperCase())!!
  }
  throw Exception("Please specify code block language")
}
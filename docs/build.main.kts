// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Repository("https://repo1.maven.org/maven2/")
@file:DependsOn("net.sourceforge.plantuml:plantuml:1.2023.7")

import net.sourceforge.plantuml.FileFormat
import net.sourceforge.plantuml.FileFormatOption
import net.sourceforge.plantuml.SourceFileReader
import net.sourceforge.plantuml.error.PSystemError
import org.w3c.dom.*
import java.nio.file.Files
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.Transformer
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathExpression
import javax.xml.xpath.XPathFactory

val defaultFontSize = "14"
val noteBackgroundColor = "#EBEBEB"

var start = System.currentTimeMillis()

val outDir: Path = Path.of("out").toAbsolutePath()
Files.createDirectories(outDir)

val svgFileFormat = FileFormatOption(FileFormat.SVG, /* withMetadata = */ false)
Files.newDirectoryStream(Path.of(".").toAbsolutePath(), "*.puml").use { inFiles ->
  for (inFile in inFiles) {
    if (inFile.fileName.toString().contains("-theme.")) {
      continue
    }

    val sourceFileReader = SourceFileReader(inFile.toFile(), outDir.toFile(), svgFileFormat)
    val result = sourceFileReader.generatedImages
    if (result.size == 0) {
      System.err.println("warning: no image in $inFile")
      continue
    }

    for (s in sourceFileReader.blocks) {
      val diagram = s.diagram
      if (diagram is PSystemError) {
        System.err.println("status=ERROR")
        System.err.println("lineNumber=" + diagram.lineLocation.position)
        for (error in diagram.errorsUml) {
          System.err.println("label=" + error.error)
        }
      }
    }
  }
}

println("Generate SVG in: ${System.currentTimeMillis() - start} ms")
start = System.currentTimeMillis()

val dbFactory: DocumentBuilderFactory = DocumentBuilderFactory.newDefaultInstance()
val xPathFactory: XPathFactory = XPathFactory.newDefaultInstance()
val textFillXPath: XPathExpression = xPathFactory.newXPath().compile("/svg/g/text")
val rectFillXPath: XPathExpression = xPathFactory.newXPath().compile("/svg/g/rect")
val lineStyleXPath: XPathExpression = xPathFactory.newXPath().compile("/svg/g/line")
val pathFillXPath: XPathExpression = xPathFactory.newXPath().compile("/svg/g/path")

Files.newDirectoryStream(outDir, "*.svg").use { svgFiles ->
  for (svgFile in svgFiles) {
    transformSvg(svgFile)
  }
}

println("Transform SVG in: ${System.currentTimeMillis() - start} ms")

fun transformSvg(svgFile: Path) {
  val dBuilder = dbFactory.newDocumentBuilder()
  val content = Files.readString(svgFile)
  val document = dBuilder.parse(content.byteInputStream())
  if (!content.contains("xlink:")) {
    document.documentElement.removeAttribute("xmlns:xlink")
  }

  val classNameToBuilder = linkedMapOf<String, String>()

  val textNodes = textFillXPath.evaluate(document, XPathConstants.NODESET) as NodeList
  for (i in 0 until textNodes.length) {
    val element = textNodes.item(i) as Element
    val fill = element.getAttributeNode("fill") ?: continue

    if (element.getAttribute("lengthAdjust") == "spacing") {
      element.removeAttribute("lengthAdjust")
    }

    if (fill.value == "#000000") {
      element.removeAttributeNode(fill)
    }
    if (hasOnlyAttributes(element.attributes, "font-", listOf("font-family", "font-size"))) {
      extractFontStyle(element, classNameToBuilder)
    }
  }

  val rectNodes = rectFillXPath.evaluate(document, XPathConstants.NODESET) as NodeList
  for (i in 0 until rectNodes.length) {
    val element = rectNodes.item(i) as Element
    val style = element.getAttributeNode("style") ?: continue
    val fill = element.getAttributeNode("fill") ?: continue
    if (style.value == "stroke:#383838;stroke-width:1.0;") {
      applyBackgroundAndBorder("process", element, style, fill, classNameToBuilder)
    }
    else if (style.value == "stroke:#FEFECE;stroke-width:1.5;") {
      applyBackgroundAndBorder("node", element, style, fill, classNameToBuilder)
    }
  }

  val lineNodes = lineStyleXPath.evaluate(document, XPathConstants.NODESET) as NodeList
  for (i in 0 until lineNodes.length) {
    val element = lineNodes.item(i) as Element
    val style = element.getAttributeNode("style") ?: continue
    if (style.value == "stroke:#A80036;stroke-width:1.0;") {
      classNameToBuilder.computeIfAbsent("arrow") {
        """
        ${style.value}
        """.trimIndent()
      }
      element.removeAttributeNode(style)
      element.setAttribute("class", "arrow")
    }
  }

  val pathNodes = pathFillXPath.evaluate(document, XPathConstants.NODESET) as NodeList
  for (i in 0 until pathNodes.length) {
    val element = pathNodes.item(i) as Element
    val style = element.getAttributeNode("style") ?: continue
    val fill = element.getAttributeNode("fill") ?: continue
    if (fill.value == noteBackgroundColor) {
      applyBackgroundAndBorder("note", element, style, fill, classNameToBuilder)
    }
  }

  appendStyleElement(document, classNameToBuilder)

  // re-format SVG
  val transformer: Transformer = TransformerFactory.newDefaultInstance().newTransformer()
  transformer.setOutputProperty(OutputKeys.INDENT, "yes")
  @Suppress("HttpUrlsUsage")
  transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
  // so, the first node of insertAdjacentHTML result will be svg and not comment
  transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")

  val fileHeader = "<!-- Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license. -->\n".encodeToByteArray()
  Files.newOutputStream(svgFile).buffered().use { fileOut ->
    fileOut.write(fileHeader)
    transformer.transform(DOMSource(document.documentElement), StreamResult(fileOut))
  }
}

fun hasOnlyAttributes(list: NamedNodeMap, prefix: String, names: List<String>): Boolean {
  val nameSet = names.toMutableSet()
  for (i in 0 until list.length) {
    val attribute = list.item(i) as Attr
    val name = attribute.name
    if (name.startsWith(prefix) && !nameSet.remove(name)) {
      return false
    }
  }

  return nameSet.isEmpty()
}

@Suppress("JavaMapForEach")
fun appendStyleElement(document: Document, classNameToBuilder: Map<String, String>) {
  val styleElement = document.createElement("style")
  val builder = StringBuilder()
  builder.append('\n')
  builder.append("""  @import url('https://fonts.googleapis.com/css?family=Roboto|Roboto+Mono&display=swap');""")
  classNameToBuilder.forEach { name, content ->
    builder.append("\n  .").append(name).append(" {")
    content.lineSequence().iterator().forEach { builder.append("\n    ").append(it.trim()) }
    builder.append("\n  }")
  }
  builder.append('\n')
  styleElement.textContent = builder.toString()
  document.documentElement.insertBefore(styleElement, document.getElementsByTagName("defs").item(0))
}

var lastUsedMonoFont: String? = null

fun extractFontStyle(element: Element, classNameToBuilder: MutableMap<String, String>) {
  val family = element.getAttributeNode("font-family")
  val size = element.getAttributeNode("font-size")

  var fontFamily = family.value
  if (lastUsedMonoFont == null && (fontFamily.contains("Mono") || fontFamily == "monospace")) {
    lastUsedMonoFont = fontFamily
  }

  val className: String
  if (fontFamily != lastUsedMonoFont && size.value == defaultFontSize) {
    fontFamily = "'Roboto', sans-serif"
    className = "text"
  }
  else if (size.value == defaultFontSize) {
    fontFamily = "'Roboto Mono', monospace"
    className = "code"
  }
  else if (size.value == "10") {
    // legend
    return
  }
  else {
    throw UnsupportedOperationException("font combination is unknown (fontFamily=$fontFamily, lastUsedMonoFont=$lastUsedMonoFont)")
  }

  classNameToBuilder.computeIfAbsent(className) {
    """
    font-family: $fontFamily;
    font-size: ${size.value}px;
    """.trimIndent()
  }

  element.removeAttributeNode(family)
  element.removeAttributeNode(size)

  element.setAttribute("class", className)
}

fun applyBackgroundAndBorder(className: String, element: Element, style: Attr, fill: Attr, classNameToBuilder: MutableMap<String, String>) {
  classNameToBuilder.computeIfAbsent(className) {
    """
    ${style.value.removeSuffix(";").replace(";", ";\n")};
    fill: ${fill.value};
    """.trimIndent()
  }
  element.removeAttributeNode(style)
  element.removeAttributeNode(fill)
  element.setAttribute("class", className)
}
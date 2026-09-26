// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.licenses

import tools.jackson.core.ObjectWriteContext
import tools.jackson.core.PrettyPrinter
import tools.jackson.core.json.JsonFactory
import tools.jackson.core.util.DefaultIndenter
import tools.jackson.core.util.DefaultPrettyPrinter
import java.nio.file.Files
import java.nio.file.Path

class LibraryLicensesListGenerator(val libraryLicenses: List<LibraryLicense>) {
  fun generateHtml(file: Path) {
    val out = StringBuilder()
    out.append("""
     <style>
     table {
       width: 560px;
     }

     th {
       border:0pt;
       text - align: left;
     }

     td {
       padding - bottom: 11px;
     }

       .firstColumn {
         width: 410px;
         padding - left: 16px;
         padding - right: 50px;
       }

       .secondColumn {
         width: 150px;
         padding - right: 28px;
       }

       .name {
         color: #4a78c2;
         margin - right: 5px;
       }

       .version {
         color: #888888;
         line - height: 1.5em;
         white - space: nowrap;
       }

       .licence {
         color: #779dbd;
       }
     </style >
   """.trimIndent())
    out.append("\n<table>")
    out.append("\n<tr><th class=\"firstColumn\">Software</th><th class=\"secondColumn\">License</th></tr>")

    for (lib in libraryLicenses) {
      val libKey = ("${lib.presentableName}_${lib.version ?: ""}").replace(' ', '_')
      // id here is needed because of a bug IDEA-188262
      val name = if (lib.url == null) {
        "<span class=\"name\">${lib.presentableName}</span>"
      }
      else {
        "<a id=\"${libKey}_lib_url\" class=\"name\" href=\"${lib.url}\">${lib.presentableName}</a>"
      }
      val license = if (lib.getLibraryLicenseUrl() != null) {
        "<a id=\"${libKey}_license_url\" class=\"licence\" href=\"${lib.getLibraryLicenseUrl()}\">${lib.license}</a>"
      }
      else {
        "<span class=\"licence\">${lib.license}</span>"
      }
      out.append('\n')
      out.append(generateHtmlLine(name = name, libVersion = lib.version ?: "", license = license))
    }

    out.append("\n</table>")
    Files.createDirectories(file.parent)
    Files.writeString(file, out)
  }

  fun generateJson(file: Path) {
    Files.createDirectories(file.parent)
    Files.newOutputStream(file).use { out ->
      val jsonFactory = JsonFactory()
      val writeContext = object : ObjectWriteContext.Base() {
        override fun tokenStreamFactory() = jsonFactory
        override fun getPrettyPrinter(): PrettyPrinter = DefaultPrettyPrinter().withObjectIndenter(DefaultIndenter("  ", "\n"))
      }
      jsonFactory.createGenerator(writeContext, out).use { writer ->
        writer.writeStartArray()
        for (entry in libraryLicenses) {
          writer.writeStartObject()

          writer.writeStringProperty("name", entry.presentableName)
          writer.writeStringProperty("url", entry.url)
          writer.writeStringProperty("version", entry.version)
          writer.writeStringProperty("license", entry.license)
          writer.writeStringProperty("licenseUrl", entry.getLibraryLicenseUrl())

          writer.writeEndObject()
        }
        writer.writeEndArray()
      }
    }
  }
}

private fun generateHtmlLine(name: String, libVersion: String, license: String): String {
  return """
    <tr valign="top">
      <td class="firstColumn">$name <span class="version">$libVersion</span></td>
      <td class="secondColumn">$license</td>
    </tr>
    """.trimIndent()
}

// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.vmOptions

import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.annotations.PropertyKey

internal object VMOptionsParser {
  private val LOG = Logger.getInstance(VMOptionsParser::class.java)

  internal fun parseXXOptions(text: String) : List<VMOption> {
    val lines = text.lineSequence().drop(1)
    val options = lines.mapNotNull {
      val lbraceIndex = it.indexOf("{")
      if (lbraceIndex == -1) return@mapNotNull null
      val rbraceIndex = it.indexOf("}")
      if (rbraceIndex == -1) return@mapNotNull null
      val kind = it.substring(lbraceIndex, rbraceIndex)
      val optionKind = if (kind.contains("product")) {
        VMOptionKind.Product
      }
      else if (kind.contains("experimental")) {
        VMOptionKind.Experimental
      }
      else if (kind.contains("diagnostic")) {
        VMOptionKind.Diagnostic
      }
      else {
        return@mapNotNull null
      }
      val fragments = it.split(" ").filter { part -> part.isNotBlank() }
      if (fragments.isEmpty()) return@mapNotNull null
      val indexOfEq = fragments.indexOf("=")
      val maybeDefault = fragments.getOrNull(indexOfEq + 1) ?: return@mapNotNull null
      val default  = if (maybeDefault.startsWith("{")) {
        null
      } else {
        maybeDefault
      }
      VMOption(fragments[1], fragments[0], default, optionKind, null, VMOptionVariant.XX)
    }.toList()

    return options
  }

  internal fun parseXOptions(stderr: String): List<VMOption>? {
    var tailIndex = stderr.indexOf("These extra options are subject to change without notice.")
    if (tailIndex == -1) {
      tailIndex = stderr.indexOf("The -X options are non-standard and subject to change without notice.")
    }
    if (tailIndex == -1) return null
    val separators = charArrayOf(' ', '<')

    return parseLines(ParsingType.JavaExtraOptions, stderr.substring(0, tailIndex).trimStart().lines(), mapOf("-X" to VMOptionVariant.X, "--" to VMOptionVariant.DASH_DASH), separators)
  }

  internal fun parseJavacDoubleDashedOptions(input: String): List<VMOption>? {
    val lines = input.lines()
    val lastLine = lines.indexOf("")
    if (lastLine == -1) return null
    val beforeStartLine = lines.indexOf("where possible options include:")
    if (beforeStartLine == -1) return null

    return parseLines(ParsingType.JavacStandardOptions, lines.subList(beforeStartLine + 1, lastLine), mapOf("--" to VMOptionVariant.DASH_DASH), charArrayOf(' '))
  }

  private fun parseLines(type: ParsingType, lines: List<String>, allowedOptionsStart: Map<String, VMOptionVariant>, separators: CharArray): List<VMOption> {
    val options = ArrayList<VMOption>()
    var currentOption: OptionBuilder? = null
    for (line in lines) {
      val trimmed = line.trim()

      var variant : VMOptionVariant? = null
      for ((startString, value) in allowedOptionsStart) {
        if (trimmed.startsWith(startString)) {
          variant = value
          break
        }
      }

      if (variant != null) {
        if (currentOption != null) {
          options.add(currentOption.build())
        }
        val indexOfSeparator = trimmed.indexOfAny(separators)
        if (indexOfSeparator != -1) {
          currentOption = OptionBuilder(type, variant, trimmed.substring(variant.prefix().length, indexOfSeparator))
          val docCandidate = trimmed.substring(indexOfSeparator).trim()
          if (docCandidate.startsWith("<")) {
            // Option has a format "-opt <path> docs" - documentation starts after second space
            currentOption.doc.add(docCandidate)
          } else {
            // Option has a format "-opt docs" - documentation starts after first space
            currentOption.doc.add(docCandidate)
          }
        }
        else {
          currentOption = OptionBuilder(type,variant, trimmed.substring(variant.prefix().length))
        }
      }
      else {
        currentOption?.doc?.add(trimmed)
      }
    }
    if (currentOption != null) {
      options.add(currentOption.build())
    }

    return options
  }

  private class OptionBuilder(private val type: ParsingType, val variant: VMOptionVariant, name: String) {
    val name = type.parseName(name)
    val doc = ArrayList<String>()


    fun build(): VMOption {
      val vmOptionMapKey = "${variant.prefix()}$name"
      val key = type.optionDescriptionPropertyKey[vmOptionMapKey]
      val description = if (key != null) {
        VMOptionsBundle.message(key)
      } else {
        LOG.warn("Option $vmOptionMapKey is not localized. Output of java command will be used instead. Please, localize it in VMOptionsBundle")
        doc.joinToString(separator = " ")
      }
      return VMOption(name, type = null, defaultValue = null, kind = type.kind, doc = description, variant)
    }
  }

  private interface ParsingType {
    val optionDescriptionPropertyKey: Map<String, @PropertyKey(resourceBundle = BUNDLE) String>

    val kind: VMOptionKind

    fun parseName(input: String): String

    object JavaExtraOptions : ParsingType {
      override val optionDescriptionPropertyKey: Map<String, @PropertyKey(resourceBundle = "messages.VMOptionsBundle") String> = mapOf(
        Pair("-Xbatch", "vm.option.batch.description"),
        Pair("-Xbootclasspath:", "vm.option.bootclasspath.description"),
        Pair("-Xbootclasspath/p:", "vm.option.bootclasspath.p.description"),
        Pair("-Xbootclasspath/a:", "vm.option.bootclasspath.a.description"),
        Pair("-Xdebug", "vm.option.debug.description"),
        Pair("-Xcheck:jni", "vm.option.check.jni.description"),
        Pair("-Xcomp", "vm.option.comp.description"),
        Pair("-Xdiag", "vm.option.diag.description"),
        Pair("-Xfuture", "vm.option.future.description"),
        Pair("-Xinternalversion", "vm.option.internalversion.description"),
        Pair("-Xlog:", "vm.option.log.description"),
        Pair("-Xloggc:", "vm.option.loggc.description"),
        Pair("-Xmixed", "vm.option.mixed.description"),
        Pair("-Xmn", "vm.option.mn.description"),
        Pair("-Xms", "vm.option.ms.description"),
        Pair("-Xmx", "vm.option.mx.description"),
        Pair("-Xrs", "vm.option.rs.description"),
        Pair("-Xnoclassgc", "vm.option.noclassgc.description"),
        Pair("-Xshare:auto", "vm.option.share.auto.description"),
        Pair("-Xshare:off", "vm.option.share.off.description"),
        Pair("-Xshare:on", "vm.option.share.on.description"),
        Pair("-XshowSettings", "vm.option.showSettings.description"),
        Pair("-XshowSettings:all", "vm.option.showSettings.all.description"),
        Pair("-XshowSettings:locale", "vm.option.showSettings.locale.description"),
        Pair("-XshowSettings:properties", "vm.option.showSettings.properties.description"),
        Pair("-XshowSettings:vm", "vm.option.showSettings.vm.description"),
        Pair("-XshowSettings:system", "vm.option.showSettings.system.description"),
        Pair("-Xss", "vm.option.ss.description"),
        Pair("-Xverify", "vm.option.verify.description"),
        Pair("-Xincgc", "vm.option.incgc.description"),
        Pair("-Xprof", "vm.option.prof.description"),
        Pair("-Xint", "vm.option.int.description"),
        Pair("--add-reads", "vm.option.add.reads.description"),
        Pair("--add-opens", "vm.option.add.opens.description"),
        Pair("--limit-modules", "vm.option.limit.modules.description"),
        Pair("--patch-module", "vm.option.patch.module.description"),
        Pair("--finalization=", "vm.option.finalization.description"),
        Pair("--add-exports", "vm.option.add.exports.description"),
        Pair("--source", "vm.option.source.description"),
        Pair("--disable-@files", "vm.option.disable.files.description"),
        Pair("--illegal-access=", "vm.option.illegal.access.description")
      )

      override val kind: VMOptionKind = VMOptionKind.Product

      override fun parseName(input: String): String {
        return input.split("<").first()
      }
    }

    object JavacStandardOptions : ParsingType {
      override val optionDescriptionPropertyKey: Map<String, @PropertyKey(resourceBundle = "messages.VMOptionsBundle") String> = mapOf(
        Pair("--add-modules", "vm.option.add.modules.description"),
        Pair("--boot-class-path", "vm.option.boot.classpath.description"),
        Pair("--class-path", "vm.option.classpath.description"),
        Pair("-deprecation", "vm.option.deprecation.description"),
        Pair("--enable-preview", "vm.option.enable.preview.description"),
        Pair("--help,", "vm.option.help.description"),
        Pair("--help-extra,", "vm.option.help.extra.description"),
        Pair("--limit-modules", "vm.option.limit.modules.javac.description"),
        Pair("--module", "vm.option.module.description"),
        Pair("--module-path", "vm.option.module.path.description"),
        Pair("--module-source-path", "vm.option.module.source.path.description"),
        Pair("--module-version", "vm.option.module.version.description"),
        Pair("--processor-module-path", "vm.option.processor.module.path.description"),
        Pair("--processor-path", "vm.option.processor.path.description"),
        Pair("--release", "vm.option.release.description"),
        Pair("--source", "vm.option.source.release.description"),
        Pair("--source-path", "vm.option.source.path.description"),
        Pair("--system", "vm.option.system.description"),
        Pair("--target", "vm.option.target.description"),
        Pair("--upgrade-module-path", "vm.option.upgrade.module.path.description"),
        Pair("--version,", "vm.option.version.description"),
      )

      override val kind: VMOptionKind = VMOptionKind.Standard

      override fun parseName(input: String): String {
        // parsing names "-opt:{arguments}"
        input.extractPrefix(":{", ":")?.let { return it }

        // parsing names "-opt[=value]"
        input.extractPrefix("[", "")?.let { return it }

        return input.extractPrefix("<", shouldHaveTwoParts = false) ?: throw IllegalArgumentException("Cannot parse option name: $input")
      }

      private fun String.extractPrefix(delimiter: String, suffix: String = "", shouldHaveTwoParts: Boolean = true): String? {
        val parts = split(delimiter)
        return if (parts.size == 2 || !shouldHaveTwoParts) parts.first().trimEnd(',') else null
      }
    }
  }
}
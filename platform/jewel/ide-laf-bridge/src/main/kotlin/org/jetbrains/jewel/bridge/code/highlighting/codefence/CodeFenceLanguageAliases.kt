// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.bridge.code.highlighting.codefence

import com.intellij.openapi.util.text.StringUtil

// Copied from org.intellij.plugins.markdown.injection.aliases.CodeFenceLanguageAliases
internal object CodeFenceLanguageAliases {
    private data class Entry(val id: String, val main: String, val aliases: Set<String>)

    private val aliases =
        setOf(
            Entry("go", "go", setOf("golang")),
            Entry("HCL", "hcl", setOf("hcl")),
            Entry("ApacheConfig", "apacheconf", setOf("aconf", "apache", "apacheconfig")),
            Entry("Batch", "batch", setOf("bat", "batchfile")),
            Entry("CoffeeScript", "coffeescript", setOf("coffee", "coffee-script")),
            Entry("JavaScript", "javascript", setOf("js", "node")),
            Entry("Markdown", "markdown", setOf("md")),
            Entry("PowerShell", "powershell", setOf("posh", "pwsh")),
            Entry("Python", "python", setOf("python2", "python3", "py")),
            Entry("R", "r", setOf("rlang", "rscript")),
            Entry("RegExp", "regexp", setOf("regex")),
            Entry("Ruby", "ruby", setOf("ruby", "rb")),
            Entry("Yaml", "yaml", setOf("yml")),
            Entry("Kotlin", "kotlin", setOf("kt", "kts")),
            Entry("HCL-Terraform", "terraform", setOf("hcl-terraform", "tf")),
            Entry("C#", "csharp", setOf("cs", "c#")),
            Entry("F#", "fsharp", setOf("fs", "f#")),
            Entry("Shell Script", "shell", setOf("shell script", "bash", "zsh", "sh")),
        )

    // Jewel specific map
    private val aliasToMainExtension =
        mapOf(
            "asciidoc" to "adoc",
            "asciidoctor" to "adoc",
            "batch" to "bat",
            "bicep parameters" to "bicepparam",
            "bicep params" to "bicepparam",
            "clojure" to "clj",
            "coffeescript" to "coffee",
            "c++" to "cpp",
            "c#" to "cs",
            "csharp" to "cs",
            "docker" to "dockerfile",
            "dockerfile" to "dockerfile",
            "containerfile" to "containerfile",
            "erlang" to "erl",
            "f#" to "fs",
            "fsharp" to "fs",
            "ignore" to "gitignore",
            "properties" to "conf",
            "javascript" to "js",
            "java server pages" to "jsp",
            "jstl" to "jsp",
            "julia markdown" to "jl",
            "juliamarkdown" to "jl",
            "kotlin" to "kt",
            "tex" to "sty",
            "latex" to "tex",
            "bibtex" to "bib",
            "makefile" to "mak",
            "markdown" to "md",
            "objective-c" to "m",
            "objective-c++" to "mm",
            "perl6" to "p6",
            "powershell" to "ps1",
            "ps" to "ps1",
            "pwsh" to "ps1",
            "python" to "py",
            "regex" to "RegExp", // Curiously, TextMate can only highlight regex if the file is .RegExp.
            "regexp" to "RegExp",
            "restructuredText" to "rst",
            "ruby" to "rb",
            "rust" to "rs",
            "search result" to "code-search",
            "shaderlab" to "shader",
            "shell script" to "sh",
            "shellscript" to "sh",
            "shell" to "sh",
            "bash" to "sh",
            "zsh" to "sh",
            "terraform" to "tf",
            "html (twig)" to "html.twig",
            "twig" to "html.twig",
            "typescript" to "ts",
            "visual basic" to "vb",
            "viml" to "vim",
            "vim script" to "vim",
        )

    fun findRegisteredEntry(value: String): String? {
        val lower = value.lowercase()
        val entry = aliases.singleOrNull { lower == it.main || lower in it.aliases }
        return entry?.id
    }

    fun findMainAlias(id: String): String = findMainAliasIfRegistered(id) ?: StringUtil.toLowerCase(id)

    fun findMainAliasIfRegistered(id: String): String? = aliases.singleOrNull { id == it.id }?.main

    /**
     * Jewel specific code. This is a way to turn a language alias to its main extension.
     *
     * So, `python` -> `py`
     *
     * With this, TextMate is able to correctly highlight the syntax of the code block.
     */
    fun findExtensionGivenAlias(alias: String): String {
        val lowercaseAlias = alias.lowercase().split(" ").first()
        return aliasToMainExtension[lowercaseAlias] ?: lowercaseAlias
    }
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.standalone.code.highlighting.languages

import org.jetbrains.jewel.intui.standalone.code.highlighting.LanguageGrammar
import org.jetbrains.jewel.intui.standalone.code.highlighting.TokenRule

internal val JSON =
    LanguageGrammar(
        name = "json",
        aliases =
            listOf(
                "4dform",
                "4dproject",
                "avsc",
                "bowerrc",
                "cssmap",
                "geojson",
                "gltf",
                "har",
                "ice",
                "ipynb",
                "jscsrc",
                "jslintrc",
                "jsmap",
                "json.example",
                "json-tmlanguage",
                "jsonl",
                "jsonld",
                "mcmeta",
                "sarif",
                "slnlaunch",
                "tact",
                "tfstate",
                "tfstate.backup",
                "topojson",
                "tsmap",
                "vuerc",
                "webapp",
                "webmanifest",
                "yy",
                "yyp",
            ),
        rules =
            listOf(
                // Comments must come first
                // Strict JSON has no comments, this is a feature of JSONC. No harm in highlighting comments on all
                // possible aliases above, though.
                TokenRule.comment("/\\*[\\s\\S]*?\\*/"),
                TokenRule.comment("//[^\n]*"),
                // string.json support.type.property-name.json
                // Object keys must come before the generic string rule since an object key is declared just like a
                // string value
                TokenRule.propertyKey("\"(?:[^\"\\\\]|\\\\.)*\"(?=\\s*:)"),
                // String values
                TokenRule.string("\"(?:[^\"\\\\]|\\\\.)*\""),
                // constant.language.json
                TokenRule.constant("\\b(?:true|false|null)\\b"),
                // constant.numeric.json, but without the tons of comments because we can't afford it here :P
                TokenRule.number("-?(?:0|[1-9]\\d*)(?:\\.\\d+)?(?:[eE][+-]?\\d+)?"),
            ),
    )

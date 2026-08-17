// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.standalone.code.highlighting.languages

import org.jetbrains.jewel.intui.standalone.code.highlighting.LanguageGrammar
import org.jetbrains.jewel.intui.standalone.code.highlighting.TokenRule
import org.jetbrains.jewel.intui.standalone.code.highlighting.TokenType

// Patterns ported from plugins/textmate/lib/bundles/yaml/syntaxes/yaml-1.2.tmLanguage.json.
//
// Differences you can see:
//  - Plain scalars in value position stay unstyled, so `value` in `key: value` is plain where TextMate
//    colors it as a string. Only the tree tells the bundle it is a value and not a key.
//  - Block scalars (`key: |`, `key: >`) stay unstyled for the same reason. The portable spelling of the
//    header would color every `|` and `>` in the file.

// The end-of-plain-scalar lookahead every constant.* rule in the bundle carries.
private const val VALUE_END = "(?=[\\t ]++#|[\\t ]*+(?>[\\r\\n,\\]}]|:[\\r\\n\\t ,\\[\\]{}]|\\z))"

// c-indicator exclusion set: the first character of a plain scalar, from #block-map-key-plain.
private const val PLAIN_FIRST = "[\\x{85}[^-?:,\\[\\]{}#&*!|>'\"%@` \\p{Cntrl}\\p{Cs}\\x{FEFF}\\x{FFFE}\\x{FFFF}]]"

// Plain scalar body, from the #block-mapping begin lookahead.
private const val PLAIN_TAIL = "(?>[^:#\\r\\n]++|:(?![\\r\\n\\t ])|(?<! |\\t)#++)*+"

// A key ends at `[\t ]*:` followed by whitespace, from #block-map-key-plain's end pattern.
private const val KEY_END = "(?=[\\t ]*+:(?:[\\r\\n\\t ]|\\z))"

// Leading indentation plus an optional block-sequence marker, matched but not colored.
private const val KEY_INDENT = "(?m)^[ \\t]*+(?:-[ \\t]++)?+"

// KEY_INDENT anchors the two block-mapping rules to a line start, so a flow map written on one line needs
// its own pair. These three come from #flow-sequence-map-key's begin and #flow-key-plain-in's end: flow
// context is "after `{`, `[`, `,` or a space", and in flow the `,[]{}` characters also close a scalar.
private const val FLOW_KEY_BEHIND = "(?m)(?<=[\\t ,\\[{]|^)"

private const val FLOW_TAIL = "(?>[^:#,\\[\\]{}\\r\\n]++|:(?![\\r\\n\\t ,\\[\\]{}])|(?<! |\\t)#++)*+"

private const val FLOW_KEY_END = "(?=[\\t ]*+:(?:[\\r\\n\\t ,\\[\\]{}]|\\z))"

internal val YAML =
    LanguageGrammar(
        name = "yaml",
        aliases =
            listOf(
                "cff",
                "eyaml",
                "eyml",
                "mir",
                "reek",
                "rviz",
                "sublime-syntax",
                "syntax",
                "winget",
                "yaml.sed",
                "yaml",
                "yaml-tmlanguage",
                "yml.mysql",
                "yml",
            ),
        rules =
            listOf(
                // comment.line.number-sign.yaml — the # must be preceded by whitespace or start of line,
                // so `foo#bar` is a scalar rather than a comment
                TokenRule.comment("(?m)(?<=[\\x{FEFF}\\t ]|^)#[^\\r\\n]*+"),
                // meta.directives.yaml — keyword.other.directive.yaml.yaml on the name,
                // constant.numeric.yaml-version.yaml on the version
                TokenRule("(?m)^(%)(YAML)([\\t ]++)(1\\.[0-3])", mapOf(2 to TokenType.KEYWORD, 4 to TokenType.NUMBER)),
                // keyword.other.directive.tag.yaml, from #directives
                TokenRule("(?m)^(%)(TAG)(?>([\\t ]++)((!)(?>[0-9A-Za-z-]*+(!))?+))?+", mapOf(2 to TokenType.KEYWORD)),
                // entity.other.document.begin.yaml / .end.yaml. No TokenType corresponds to
                // entity.other.document, so KEYWORD is our choice rather than the bundle's.
                TokenRule.keyword("(?m)^---(?=[\\r\\n\\t ]|\\z)"),
                TokenRule.keyword("(?m)^\\.{3}(?=[\\r\\n\\t ]|\\z)"),
                // meta.map.key.yaml string.quoted.{double,single}.yaml entity.name.tag.yaml
                TokenRule(
                    KEY_INDENT + "(\"(?>[^\\\\\"]++|\\\\.)*+\"|'(?>[^']++|'')*+')" + KEY_END,
                    mapOf(1 to TokenType.PROPERTY_KEY),
                ),
                // meta.map.key.yaml string.unquoted.plain.yaml entity.name.tag.yaml
                TokenRule(
                    KEY_INDENT + "((?:" + PLAIN_FIRST + "|[?:-](?![\\r\\n\\t ]))" + PLAIN_TAIL + ")" + KEY_END,
                    mapOf(1 to TokenType.PROPERTY_KEY),
                ),
                // The same two, for a flow map: meta.flow.map.key.yaml … entity.name.tag.yaml. Both stay
                // ahead of the plain string rules so `{"foo": 1}` reads as a key rather than a string.
                TokenRule(
                    FLOW_KEY_BEHIND + "(\"(?>[^\\\\\"]++|\\\\.)*+\"|'(?>[^']++|'')*+')" + FLOW_KEY_END,
                    mapOf(1 to TokenType.PROPERTY_KEY),
                ),
                TokenRule(
                    FLOW_KEY_BEHIND +
                        "((?:" +
                        PLAIN_FIRST +
                        "|[?:-](?![\\r\\n\\t ,\\[\\]{}]))" +
                        FLOW_TAIL +
                        ")" +
                        FLOW_KEY_END,
                    mapOf(1 to TokenType.PROPERTY_KEY),
                ),
                // string.quoted.double.yaml / string.quoted.single.yaml
                TokenRule.string("\"(?>[^\\\\\"]++|\\\\.)*+\""),
                TokenRule.string("'(?>[^']++|'')*+'"),
                // keyword.control.flow.anchor.yaml
                TokenRule.keyword("&[\\x{85}[^ ,\\[\\]{}\\p{Cntrl}\\p{Cs}\\x{FEFF}\\x{FFFE}\\x{FFFF}]]++"),
                // keyword.control.flow.alias.yaml
                TokenRule.keyword("\\*[\\x{85}[^ ,\\[\\]{}\\p{Cntrl}\\p{Cs}\\x{FEFF}\\x{FFFE}\\x{FFFF}]]++"),
                // storage.type.tag.verbatim.yaml, then storage.type.tag.shorthand.yaml, which also covers
                // storage.type.tag.non-specific.yaml (a bare `!`)
                TokenRule.keyword("!<[^>\\r\\n]*+>"),
                TokenRule.keyword("![^\\r\\n\\t ,\\[\\]{}]*+"),
                // constant.language.boolean.yaml — 1.2 knows only these six spellings; yes/no/on/off are
                // YAML 1.1 and are deliberately absent from the bundle
                TokenRule.constant("(?>true|True|TRUE|false|False|FALSE)$VALUE_END"),
                // constant.language.null.yaml
                TokenRule.constant("(?>null|Null|NULL|~)$VALUE_END"),
                // constant.numeric.*.yaml — inf/nan before float, and radix before decimal, so the more
                // specific rule wins the tie at the same offset
                TokenRule.number("[+-]?+\\.(?>inf|Inf|INF)$VALUE_END"),
                TokenRule.number("\\.(?>nan|NaN|NAN)$VALUE_END"),
                TokenRule.number("0x[0-9a-fA-F]++$VALUE_END"),
                TokenRule.number("0o[0-7]++$VALUE_END"),
                TokenRule.number("[+-]?+(?>\\.[0-9]++|[0-9]++(?>\\.[0-9]*+)?+)(?>[eE][+-]?+[0-9]++)?+$VALUE_END"),
                TokenRule.number("[+-]?+[0-9]++$VALUE_END"),
            ),
    )

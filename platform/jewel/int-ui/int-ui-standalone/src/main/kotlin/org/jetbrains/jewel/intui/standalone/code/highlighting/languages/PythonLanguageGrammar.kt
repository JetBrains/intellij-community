// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.standalone.code.highlighting.languages

import org.jetbrains.jewel.intui.standalone.code.highlighting.LanguageGrammar
import org.jetbrains.jewel.intui.standalone.code.highlighting.TokenRule

// Patterns adapted from plugins/textmate/lib/bundles/python/syntaxes/MagicPython.tmLanguage.json
// That grammar is written in (?x) extended mode with POSIX classes ([[:alpha:]]); both are rewritten here.
// #number-float, constant.numeric.float.python. Kept in the bundle's (?x) mode. The first branch is the
// leading-dot spelling (`.5`, `.5e2`) and every digit run allows `_` separators, the exponent included
// (`1e1_0`); a single hand-rolled decimal rule misses both. Group 1 is the imaginary suffix, scoped
// storage.type.imaginary.number.python, which we fold into the number.
private const val NUMBER_FLOAT =
    """(?x)
      (?<! \w)(?:
        (?:
          \.[0-9](?: _?[0-9] )*
          |
          [0-9](?: _?[0-9] )* \. [0-9](?: _?[0-9] )*
          |
          [0-9](?: _?[0-9] )* \.
        ) (?: [eE][+-]?[0-9](?: _?[0-9] )* )?
        |
        [0-9](?: _?[0-9] )* (?: [eE][+-]?[0-9](?: _?[0-9] )* )
      )([jJ])?\b
    """

// #number-dec, constant.numeric.dec.python. Group 2 is invalid.illegal.dec.python and is dropped.
private const val NUMBER_DEC =
    """(?x)
      (?<![\w\.])(?:
          [1-9](?: _?[0-9] )*
          |
          0+
          |
          [0-9](?: _?[0-9] )* ([jJ])
          |
          0 ([0-9]+)(?![eE\.])
      )\b
    """

internal val PYTHON =
    LanguageGrammar(
        name = "python",
        aliases =
            listOf(
                "cgi",
                "cpy",
                "fcgi",
                "gyp",
                "gypi",
                "ipy",
                "lmi",
                "py3",
                "py",
                "pyde",
                "pyi",
                "pyp",
                "pyt",
                "python3",
                "pyw",
                "rpy",
                "spec",
                "tac",
                "wsgi",
                "xpy",
            ),
        rules =
            listOf(
                // Comments must come first to avoid matching inside them
                TokenRule.comment("#[^\n]*"),
                // Strings — triple-quoted first, with optional r/b/u/f prefixes
                TokenRule.string("[rRbBuUfF]{0,2}\"\"\"[\\s\\S]*?\"\"\""),
                TokenRule.string("[rRbBuUfF]{0,2}'''[\\s\\S]*?'''"),
                TokenRule.string("[rRbBuUfF]{0,2}\"(?:[^\"\\\\\n]|\\\\.)*\""),
                TokenRule.string("[rRbBuUfF]{0,2}'(?:[^'\\\\\n]|\\\\.)*'"),
                // entity.name.function.decorator.python
                TokenRule.functionCall("(@[A-Za-z_][A-Za-z0-9_.]*)"),
                // def <name> / class <name>
                TokenRule.functionDeclaration("\\b(def)\\s+([A-Za-z_][A-Za-z0-9_]*)"),
                TokenRule.typeDeclaration("\\b(class)\\s+([A-Za-z_][A-Za-z0-9_]*)"),
                // keyword.control.flow.python + storage.modifier.declaration.python + operators that are words.
                // `match` and `case` are soft keywords and far too common as identifiers, so they're left out.
                TokenRule.keyword(
                    "\\b(?:def|class|lambda|return|yield|import|from|as|pass|break|continue|if|elif|else|for|" +
                        "while|try|except|finally|raise|with|assert|del|global|nonlocal|async|await)\\b"
                ),
                // keyword.operator.logical.python, group 1 of #operator
                TokenRule.operator("\\b(?:and|or|not|in|is)\\b"),
                // constant.language.python
                TokenRule.constant("\\b(?:True|False|None|NotImplemented|Ellipsis|__debug__)\\b"),
                // support.type.python — IntelliJ's predefined-symbol key, which is our BUILTIN
                TokenRule.builtin(
                    "\\b(?:bool|bytearray|bytes|complex|dict|float|frozenset|int|list|object|property|set|" +
                        "slice|str|tuple|type|super|classmethod|staticmethod)\\b"
                ),
                // support.function.builtin.python + variable.language.special.self
                TokenRule.builtin(
                    "\\b(?:self|cls|print|len|range|open|input|abs|all|any|enumerate|filter|format|getattr|" +
                        "hasattr|hash|id|isinstance|issubclass|iter|map|max|min|next|repr|reversed|round|" +
                        "setattr|sorted|sum|zip|vars|dir|eval|exec|divmod|chr|ord|hex|oct|bin|callable)\\b"
                ),
                // entity.name.function.call — after keywords so `if (` stays a keyword
                TokenRule.functionCall("\\b([A-Za-z_][A-Za-z0-9_]*)\\s*(?=\\()"),
                // #number, in the bundle's own order so a float beats the decimal rule at the same offset.
                // The prefixes are storage.type.number.python in the bundle; we fold them into the number.
                TokenRule.number(NUMBER_FLOAT),
                TokenRule.number(NUMBER_DEC),
                TokenRule.number("""(?x) (?<![\w\.]) (0[xX]) (_?[0-9a-fA-F])+ \b"""),
                TokenRule.number("""(?x) (?<![\w\.]) (0[oO]) (_?[0-7])+ \b"""),
                TokenRule.number("""(?x) (?<![\w\.]) (0[bB]) (_?[01])+ \b"""),
                // #number-long, python 2 long ints
                TokenRule.number("""(?x) (?<![\w\.]) ([1-9][0-9]* | 0) ([lL]) \b"""),
            ),
    )

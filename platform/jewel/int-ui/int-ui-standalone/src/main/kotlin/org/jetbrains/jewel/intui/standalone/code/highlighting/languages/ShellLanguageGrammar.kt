// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.standalone.code.highlighting.languages

import org.jetbrains.jewel.intui.standalone.code.highlighting.LanguageGrammar
import org.jetbrains.jewel.intui.standalone.code.highlighting.TokenRule
import org.jetbrains.jewel.intui.standalone.code.highlighting.TokenType

// Patterns ported from plugins/textmate/lib/bundles/shellscript/syntaxes/shell-unix-bash.tmLanguage.json.
//
// Differences you can see, all of them because TextMate tracks which construct it is inside and we match
// one regex at a time:
//  - Case patterns read as commands: `start)` gets the command color where TextMate leaves it plain. Every
//    guard that excludes a word before `)` also excludes `$(pwd)`, which is far more common.
//  - `IFS= read -r line` leaves `read` plain, because an assignment prefix does not open command position.
//  - `declare -A m` leaves `m` plain. Only `m=` is recognized as an assignment target.
//  - Globs (`*`, `?`) and the `${...}` expansion operators stay plain. Matched flat they would color every
//    `*`, `?`, `:`, `#`, `/`, `%` and `@` in the file.
//  - Test and arithmetic operators stay plain, so `-f` in `[ -f x ]` reads as a command option instead.
//  - Backticks and `<(cmd)` are one flat string; TextMate highlights the commands inside them.
//  - An unterminated heredoc colors nothing, where TextMate runs the string to end of file.
//  - `alias` reads as a builtin rather than a keyword, and so does `$VAR`: IntelliJ has no separate color
//    for shell variables.

// Command position, which the bundle gets from a \G anchor three levels down the tree. The `((` case is
// excluded so an arithmetic operand does not read as a command.
private const val COMMAND_START =
    "(?m)(?<=^|[;|&!{`]|(?:^|[^(])\\(|(?:^|[\\t ])(?:until|while|elif|else|then|do|if) )[\\t ]*+"

// What a command cannot start with. `}`, `]` and `$(` are ours: the bundle eats them as punctuation before
// a command can start, so flat a lone `}` closing a function body would read as a command name.
private const val NOT_COMMAND_CHAR = "(?![!&|(){}\\[\\]<>#;\\t\\n ]|\\$\\(|$)"

// #command_statement's begin also refuses to start a command on a word that is really a keyword.
private const val NOT_KEYWORD =
    "(?!(?:nocorrect|readonly|function|foreach|coproc|logout|export|select|repeat|pushd|until|while|local|" +
        "case|done|elif|else|esac|popd|then|time|for|end|fi|do|in|if)(?:[\\t ]|$))"

// #numeric_literal.
private const val NUMBER =
    "(?:(?:(?:(?:(?:(0[xX][0-9A-Fa-f]+)|(0\\d+))|(\\d{1,2}#[0-9a-zA-Z@_]+))|(-?\\d+(?:\\.\\d+)))|" +
        "(-?\\d+(?:\\.\\d+)+))|(-?\\d+))"

private val NUMBER_GROUPS =
    mapOf(
        1 to TokenType.NUMBER,
        2 to TokenType.NUMBER,
        3 to TokenType.NUMBER,
        4 to TokenType.NUMBER,
        5 to TokenType.NUMBER,
        6 to TokenType.NUMBER,
    )

internal val SHELL =
    LanguageGrammar(
        name = "shellscript",
        aliases =
            listOf(
                "bash",
                "bashrc",
                "bats",
                "csh",
                "ebuild",
                "eclass",
                "envrc",
                "fish",
                "ksh",
                "openrc",
                "profile",
                "sh",
                "shell",
                "shell-script",
                "shellcheck",
                "tcsh",
                "zsh",
                "zsh-theme",
                "zshrc",
            ),
        rules =
            listOf(
                // comment.line.number-sign.shell. The # must open the line or follow whitespace, and the
                // bundle's shebang alternative collapses into this one: same scope.
                TokenRule.comment("(?m)(?<=^|[\\t ])#[^\\r\\n]*"),
                // #heredoc, all four spellings. Group 1 is the operator, the last group the body; the
                // delimiter is punctuation, so it stays bare.
                TokenRule(
                    "(?m)((?<!<)<<-)[\\t ]*+([\"'])[\\t ]*+([^\"'\\r\\n]+?)(?=\\s|;|&|<|\"|')\\2" +
                        "[^\\r\\n]*+(\\n[\\s\\S]*?)^\\t*+\\3(?=\\s|;|&|$)",
                    mapOf(1 to TokenType.OPERATOR, 4 to TokenType.STRING),
                ),
                TokenRule(
                    "(?m)((?<!<)<<(?!<))[\\t ]*+([\"'])[\\t ]*+([^\"'\\r\\n]+?)(?=\\s|;|&|<|\"|')\\2" +
                        "[^\\r\\n]*+(\\n[\\s\\S]*?)^\\3(?=\\s|;|&|$)",
                    mapOf(1 to TokenType.OPERATOR, 4 to TokenType.STRING),
                ),
                TokenRule(
                    "(?m)((?<!<)<<-)[\\t ]*+([^\"' \\t\\r\\n]+)(?=\\s|;|&|<|\"|')" +
                        "[^\\r\\n]*+(\\n[\\s\\S]*?)^\\t*+\\2(?=\\s|;|&|$)",
                    mapOf(1 to TokenType.OPERATOR, 3 to TokenType.STRING),
                ),
                TokenRule(
                    "(?m)((?<!<)<<(?!<))[\\t ]*+([^\"' \\t\\r\\n]+)(?=\\s|;|&|<|\"|')" +
                        "[^\\r\\n]*+(\\n[\\s\\S]*?)^\\2(?=\\s|;|&|$)",
                    mapOf(1 to TokenType.OPERATOR, 3 to TokenType.STRING),
                ),
                // #string — the bundle's begin/end pairs, fused. Single quotes take no escapes in shell,
                // which is why only the double-quoted forms carry one.
                TokenRule.string("\\$'(?:\\\\.|[^'\\\\])*'"),
                TokenRule.string("'[^']*'"),
                TokenRule.string("\\$?\"(?:\\\\.|[^\"\\\\])*\""),
                // string.interpolated.backtick.shell and string.interpolated.process-substitution.shell
                TokenRule.string("`(?:\\\\.|[^`\\\\])*`"),
                TokenRule.string("[><]\\([^)]*\\)"),
                // constant.character.escape.line-continuation.shell, then constant.character.escape.shell
                TokenRule.constant("\\\\(?=\\n)"),
                TokenRule.constant("\\\\."),
                // #normal_assignment_statement. Has to stay ahead of the command-name rule or `FOO=bar`
                // reads as a command. Ours: (?<![\w-])(?!-) keeps `--flag=value` out.
                TokenRule(
                    "((?<![\\w-])(?!-)[a-zA-Z_0-9-]+(?!\\w))(?:(\\[)[^\\[\\]]*(\\]))?(\\+=|-=|=)",
                    mapOf(1 to TokenType.BUILTIN, 4 to TokenType.OPERATOR),
                ),
                // #floating_keyword — keyword.control.$0.shell
                TokenRule.keyword("(?m)(?<=^|[;& \\t])(?:then|elif|else|done|end|do|if|fi)(?=[ \\t;&]|$)"),
                // #for_statement — keyword.control.for.shell, variable.other.for.shell, keyword.control.in
                TokenRule(
                    "(\\bfor\\b)[\\t ]*+((?<!\\w)[a-zA-Z_0-9-]+(?!\\w))[\\t ]*+(\\bin\\b)",
                    mapOf(1 to TokenType.KEYWORD, 2 to TokenType.BUILTIN, 3 to TokenType.KEYWORD),
                ),
                TokenRule.keyword("\\bfor\\b"),
                // #while_statement — keyword.control.while.shell
                TokenRule.keyword("\\bwhile\\b"),
                // #loop — the while/until, select and if begins, plus the done and fi ends
                TokenRule.keyword("(?<=^|[;&\\s])(?:while|until)(?=[\\s;&]|$)"),
                TokenRule(
                    "(?<=^|[;&\\s])(select)\\s+((?:[^\\s\\\\]|\\\\.)+)(?=[\\s;&]|$)",
                    mapOf(1 to TokenType.KEYWORD, 2 to TokenType.BUILTIN),
                ),
                TokenRule.keyword("(?<=^|[;&\\s])if(?=[\\s;&]|$)"),
                TokenRule.keyword("(?<=^|[;&\\s])done(?=[\\s;&)]|$)"),
                TokenRule.keyword("(?<=^|[;&\\s])fi(?=[\\s;&]|$)"),
                // #case_statement — keyword.control.case.shell, keyword.control.in, keyword.control.esac
                TokenRule(
                    "(\\bcase\\b)[\\t ]*+.+?[\\t ]*+(\\bin\\b)",
                    mapOf(1 to TokenType.KEYWORD, 2 to TokenType.KEYWORD),
                ),
                TokenRule.keyword("\\besac\\b"),
                // #pipeline — keyword.other.shell
                TokenRule.keyword("(?<=^|[;&\\s])time(?=[\\s;&]|$)"),
                // #modified_assignment_statement — storage.modifier.$0.shell
                TokenRule.keyword("(?m)(?<=^|[;&\\t ])(?:readonly|declare|typeset|export|local)(?=[\\t ;&]|$)"),
                // #function_definition — storage.type.function.shell and entity.name.function.shell
                TokenRule.functionDeclaration("[\\t ]*+(\\bfunction\\b)[\\t ]*+([^ \\t\\n\\r()=\"']+)"),
                TokenRule.functionCall("[\\t ]*+([^ \\t\\n\\r()=\"']+)[\\t ]*+\\([\\t ]*+\\)"),
                // #command_name_range, in the bundle's order: control-flow commands, builtins, #variable,
                // then any other word.
                TokenRule(COMMAND_START + "((?:continue|return|break)(?!\\w))", mapOf(1 to TokenType.KEYWORD)),
                TokenRule(
                    COMMAND_START +
                        "((?:unfunction|continue|autoload|unsetopt|bindkey|builtin|getopts|command|declare|" +
                        "unalias|history|unlimit|typeset|suspend|source|printf|unhash|disown|ulimit|return|" +
                        "which|alias|break|false|print|shift|times|umask|umask|unset|read|type|exec|eval|" +
                        "wait|echo|dirs|jobs|kill|hash|stat|exit|test|trap|true|let|set|pwd|cd|fg|bg|fc|:|" +
                        "\\.)(?!\\/)(?!\\w)(?!-))",
                    mapOf(1 to TokenType.BUILTIN),
                ),
                // #variable — ${...} before $name, so the braced form wins the tie at the $
                TokenRule.builtin("\\$\\{[^{}]*\\}"),
                TokenRule.builtin("\\$@(?!\\w)"),
                TokenRule.builtin("\\$[0-9](?!\\w)"),
                TokenRule.builtin("\\$[-*#?$!0_](?!\\w)"),
                TokenRule.builtin("\\$\\w+(?!\\w)"),
                // entity.name.function.call.shell entity.name.command.shell
                TokenRule.functionCall(
                    COMMAND_START + NOT_COMMAND_CHAR + NOT_KEYWORD + "([^ \\n\\t\\r\"'=;&\\|`\\)\\{<>]+)"
                ),
                // #support — support.function.builtin.shell for the no-op and the dot command
                TokenRule.builtin("(?<=^|[;&\\s])[:.](?=[\\s;&]|$)"),
                // constant.language.$0.shell, from #boolean. After the builtin rule, so a bare `true`
                // command still reads as a builtin, as in the bundle.
                TokenRule.constant("\\b(?:true|false)\\b"),
                // #option — constant.other.option.shell, with the begin's guard and the end fused on
                TokenRule(
                    "[\\t ]++(-(?![!&|(){\\[<>#;\\t\\n ]|$)[^\\t \\n;|&)`}\\]]*)",
                    mapOf(1 to TokenType.CONSTANT),
                ),
                // #pipeline's keyword.operator.pipe.shell, then #redirection and #redirect_number
                TokenRule.operator("[|!]"),
                TokenRule.operator("<<<"),
                TokenRule.operator("(?<![<>])(?:&>|\\d*>&\\d*|\\d*(?:>>|>|<)|\\d*<&|\\d*<>)(?![<>])"),
                TokenRule.operator("(?<=[\\t ])\\d+(?=>)"),
                // #pathname — keyword.operator.tilde.shell
                TokenRule.operator("(?m)(?<=\\s|:|=|^)~"),
                // #numeric_literal — constant.numeric.shell
                TokenRule("(?m)(?<==| |\\t|^|\\{|\\(|\\[)$NUMBER(?= |\\t|$|\\}|\\)|;)", NUMBER_GROUPS),
            ),
    )

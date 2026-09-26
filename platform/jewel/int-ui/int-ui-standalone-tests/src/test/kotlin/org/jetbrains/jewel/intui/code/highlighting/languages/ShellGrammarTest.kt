// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.code.highlighting.languages

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.jetbrains.jewel.intui.code.highlighting.colorAt
import org.jetbrains.jewel.intui.code.highlighting.testColors
import org.jetbrains.jewel.intui.standalone.code.highlighting.SimpleCodeHighlighter
import org.junit.jupiter.api.Test

internal class ShellGrammarTest {
    private val highlighter = SimpleCodeHighlighter(testColors)

    private suspend fun highlight(code: String, language: String = "bash") =
        highlighter.highlight(code, language).first()

    @Test
    fun `name and all aliases are recognized`() = runTest {
        for (identifier in listOf("shellscript", "bash", "sh", "shell", "zsh", "ksh", "csh", "fish", "bats")) {
            assertTrue(highlight("echo hi", identifier).spanStyles.isNotEmpty(), "Alias '$identifier' not recognized")
        }
    }

    @Test
    fun `comment is colored as comment`() = runTest {
        assertEquals(testColors.comment, highlight("# a comment").colorAt(0))
    }

    @Test
    fun `shebang is colored as comment`() = runTest {
        assertEquals(testColors.comment, highlight("#!/usr/bin/env bash").colorAt(0))
    }

    @Test
    fun `trailing comment is colored as comment`() = runTest {
        assertEquals(testColors.comment, highlight("echo hi # note").colorAt(8))
    }

    @Test
    fun `hash without leading whitespace does not open a comment`() = runTest {
        // The bundle only opens a comment when the # follows whitespace or starts the line
        assertNull(highlight("echo foo#bar").colorAt(8))
    }

    @Test
    fun `single and double quoted strings are colored as string`() = runTest {
        assertEquals(testColors.string, highlight("echo 'hello'").colorAt(5))
        assertEquals(testColors.string, highlight("echo \"hello\"").colorAt(5))
    }

    @Test
    fun `ansi-c and backtick spans are colored as string`() = runTest {
        assertEquals(testColors.string, highlight("echo \$'a\\nb'").colorAt(5))
        assertEquals(testColors.string, highlight("echo `date`").colorAt(5))
    }

    @Test
    fun `keywords inside a string are not colored separately`() = runTest {
        assertEquals(testColors.string, highlight("echo \"if then fi\"").colorAt(9))
    }

    @Test
    fun `control flow keywords are colored as keyword`() = runTest {
        val result = highlight("if true; then echo hi; fi")
        assertEquals(testColors.keyword, result.colorAt(0)) // if
        assertEquals(testColors.keyword, result.colorAt(9)) // then
        assertEquals(testColors.keyword, result.colorAt(23)) // fi
    }

    @Test
    fun `for in loop colors both keywords and the loop variable`() = runTest {
        val result = highlight("for i in 1 2 3; do echo \$i; done")
        assertEquals(testColors.keyword, result.colorAt(0)) // for
        assertEquals(testColors.builtin, result.colorAt(4)) // i, variable.other.for.shell
        assertEquals(testColors.keyword, result.colorAt(6)) // in
        assertEquals(testColors.keyword, result.colorAt(16)) // do
        assertEquals(testColors.keyword, result.colorAt(28)) // done
    }

    @Test
    fun `while and until are colored as keyword`() = runTest {
        assertEquals(testColors.keyword, highlight("while read line; do :; done").colorAt(0))
        assertEquals(testColors.keyword, highlight("until false; do :; done").colorAt(0))
    }

    @Test
    fun `case and esac are colored as keyword`() = runTest {
        val result = highlight("case \$x in a) ;; esac")
        assertEquals(testColors.keyword, result.colorAt(0)) // case
        assertEquals(testColors.keyword, result.colorAt(8)) // in
        assertEquals(testColors.keyword, result.colorAt(17)) // esac
    }

    @Test
    fun `select is colored as keyword`() = runTest {
        assertEquals(testColors.keyword, highlight("select x in a b; do :; done").colorAt(0))
    }

    @Test
    fun `break continue and return are colored as keyword`() = runTest {
        assertEquals(testColors.keyword, highlight("while :; do break; done").colorAt(12))
        assertEquals(testColors.keyword, highlight("while :; do continue; done").colorAt(12))
        assertEquals(testColors.keyword, highlight("f() { return 1; }").colorAt(6))
    }

    @Test
    fun `storage modifiers are colored as keyword`() = runTest {
        for (modifier in listOf("readonly", "declare", "typeset", "export", "local")) {
            assertEquals(
                testColors.keyword,
                highlight("$modifier x=1").colorAt(0),
                "'$modifier' should be colored as keyword",
            )
        }
    }

    @Test
    fun `time is colored as keyword`() = runTest { assertEquals(testColors.keyword, highlight("time ls").colorAt(0)) }

    @Test
    fun `function keyword and name are colored separately`() = runTest {
        val result = highlight("function greet { echo hi; }")
        assertEquals(testColors.keyword, result.colorAt(0))
        assertEquals(testColors.functionCall, result.colorAt(9))
    }

    @Test
    fun `posix function definition colors the name`() = runTest {
        assertEquals(testColors.functionCall, highlight("greet() { echo hi; }").colorAt(0))
    }

    @Test
    fun `shell builtins are colored as builtin`() = runTest {
        for (builtin in listOf("echo", "printf", "read", "exit", "eval", "export")) {
            val expected = if (builtin == "export") testColors.keyword else testColors.builtin
            assertEquals(expected, highlight("$builtin x").colorAt(0), "'$builtin' has the wrong color")
        }
    }

    @Test
    fun `the no-op and dot commands are colored as builtin`() = runTest {
        assertEquals(testColors.builtin, highlight(": noop").colorAt(0))
        assertEquals(testColors.builtin, highlight(". ./lib.sh").colorAt(0))
    }

    @Test
    fun `external commands are colored as function calls`() = runTest {
        assertEquals(testColors.functionCall, highlight("ls -la").colorAt(0))
        assertEquals(testColors.functionCall, highlight("git commit").colorAt(0))
        assertEquals(testColors.functionCall, highlight("./script.sh").colorAt(0))
    }

    @Test
    fun `commands are recognized after a pipe an ampersand and a subshell`() = runTest {
        assertEquals(testColors.functionCall, highlight("ls | grep x").colorAt(7))
        assertEquals(testColors.functionCall, highlight("a && b").colorAt(5))
        assertEquals(testColors.builtin, highlight("(cd /tmp && ls)").colorAt(1))
        assertEquals(testColors.builtin, highlight("\$(cd /tmp)").colorAt(2))
        assertEquals(testColors.functionCall, highlight("if x; then ls; fi").colorAt(11))
    }

    @Test
    fun `a bare argument is not colored`() = runTest {
        assertNull(highlight("git log --oneline").colorAt(4))
        assertNull(highlight("echo hello world").colorAt(10))
    }

    @Test
    fun `variables are colored as builtin`() = runTest {
        for (variable in listOf("\$HOME", "\${HOME}", "\$1", "\$@", "\$?")) {
            assertEquals(
                testColors.builtin,
                highlight("echo $variable").colorAt(5),
                "'$variable' should be colored as builtin",
            )
        }
    }

    @Test
    fun `assignment colors the target and the operator`() = runTest {
        val result = highlight("FOO=bar")
        assertEquals(testColors.builtin, result.colorAt(0))
        assertEquals(testColors.operator, result.colorAt(3))
    }

    @Test
    fun `true and false are constants as values and builtins as commands`() = runTest {
        assertEquals(testColors.constant, highlight("x=true").colorAt(2))
        assertEquals(testColors.builtin, highlight("true").colorAt(0))
    }

    @Test
    fun `options are colored as constant`() = runTest {
        assertEquals(testColors.constant, highlight("set -euo pipefail").colorAt(4))
        assertEquals(testColors.constant, highlight("curl --data=x").colorAt(7))
    }

    @Test
    fun `an option is not mistaken for a builtin of the same name`() = runTest {
        // `type` is in the bundle's builtin list, but `-type` here is an option
        assertEquals(testColors.constant, highlight("find . -type f").colorAt(8))
    }

    @Test
    fun `numbers are colored as number`() = runTest {
        for (number in listOf("42", "0x1F", "0755", "3.14", "-1")) {
            assertEquals(testColors.number, highlight("x=$number").colorAt(2), "'$number' should be a number")
        }
        assertEquals(testColors.number, highlight("exit 1").colorAt(5))
    }

    @Test
    fun `arithmetic operands are numbers and the operator is left alone`() = runTest {
        val result = highlight("x=\$((1 + 2))")
        assertEquals(testColors.number, result.colorAt(5))
        assertNull(result.colorAt(7), "#math's operators are not ported")
    }

    @Test
    fun `redirections are colored as operator`() = runTest {
        assertEquals(testColors.operator, highlight("echo hi > out.txt").colorAt(8))
        assertEquals(testColors.operator, highlight("echo hi 2> out.txt").colorAt(8)) // the fd number
        assertEquals(testColors.operator, highlight("echo hi 2> out.txt").colorAt(9))
        assertEquals(testColors.operator, highlight("ls | grep x").colorAt(3))
        assertEquals(testColors.operator, highlight("cat <<< 'x'").colorAt(4))
        assertEquals(testColors.operator, highlight("cd ~").colorAt(3))
    }

    @Test
    fun `heredoc colors the operator and the body`() = runTest {
        val code = "cat <<EOF\nhello \$name\nEOF\n"
        assertEquals(testColors.operator, highlight(code).colorAt(4))
        assertEquals(testColors.string, highlight(code).colorAt(10))
    }

    @Test
    fun `quoted and indented heredocs are colored too`() = runTest {
        val quoted = "cat <<'EOF'\nhello\nEOF\n"
        assertEquals(testColors.operator, highlight(quoted).colorAt(4))
        assertEquals(testColors.string, highlight(quoted).colorAt(12))

        val indented = "cat <<-EOF\n\thello\n\tEOF\n"
        assertEquals(testColors.operator, highlight(indented).colorAt(4))
        assertEquals(testColors.string, highlight(indented).colorAt(12))
    }

    @Test
    fun `an unterminated heredoc does not swallow the rest of the file`() = runTest {
        assertNull(highlight("cat <<EOF\nnever closed\n").colorAt(4))
    }

    @Test
    fun `a closing brace is not mistaken for a command`() = runTest {
        // The bundle's function-body block consumes it as punctuation; flat it looks like a statement start
        assertNull(highlight("f() {\n  echo hi\n}\n").colorAt(16))
    }

    @Test
    fun `arithmetic parentheses do not open command position`() = runTest {
        assertNull(highlight("((i++))").colorAt(2))
    }

    @Test
    fun `builtins inside a comment are not colored`() = runTest {
        assertEquals(testColors.comment, highlight("# echo hi").colorAt(2))
    }
}

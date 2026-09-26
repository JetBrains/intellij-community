// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.code.highlighting.languages

import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.jetbrains.jewel.intui.code.highlighting.colorAt
import org.jetbrains.jewel.intui.code.highlighting.testColors
import org.jetbrains.jewel.intui.standalone.code.highlighting.SimpleCodeHighlighter
import org.junit.jupiter.api.Test

internal class SQLGrammarTest {
    private val highlighter = SimpleCodeHighlighter(testColors)

    private suspend fun highlight(code: String, language: String = "sql") =
        highlighter.highlight(code, language).first()

    @Test
    fun `name and all aliases are recognized`() = runTest {
        for (identifier in listOf("sql", "dsql", "ddl", "dml", "cql", "prc", "tab", "udf", "viw", "db2")) {
            assertTrue(highlight("SELECT 1", identifier).spanStyles.isNotEmpty(), "Alias '$identifier' not recognized")
        }
    }

    @Test
    fun `line comment is colored as comment`() = runTest {
        assertEquals(testColors.comment, highlight("-- a comment").colorAt(0))
    }

    @Test
    fun `line comment ends at the newline`() = runTest {
        val result = highlight("-- c\nSELECT")
        assertEquals(testColors.comment, result.colorAt(0))
        assertEquals(testColors.keyword, result.colorAt(5))
    }

    @Test
    fun `line comment wins over the minus operator`() = runTest {
        assertEquals(testColors.comment, highlight("SELECT 1 -- note").colorAt(9))
    }

    @Test
    fun `block comment is colored as comment`() = runTest {
        assertEquals(testColors.comment, highlight("/* block */").colorAt(0))
    }

    @Test
    fun `keyword inside comment is not colored as keyword`() = runTest {
        val result = highlight("-- SELECT FROM")
        assertEquals(testColors.comment, result.colorAt(3))
        assertNotEquals(testColors.keyword, result.colorAt(3))
    }

    @Test
    fun `single-quoted string is colored as string`() = runTest {
        assertEquals(testColors.string, highlight("'hello'").colorAt(0))
    }

    @Test
    fun `doubled quote produces two adjacent strings`() = runTest {
        // The bundle has no rule for SQL's '' escape: "'it''s'" is scanned as "'it'" followed by "'s'",
        // so index 6 is still inside a string, but as the closing quote of the second one
        assertEquals(testColors.string, highlight("'it''s'").colorAt(6))
    }

    @Test
    fun `national character literal includes its N prefix`() = runTest {
        assertEquals(testColors.string, highlight("N'x'").colorAt(0))
    }

    @Test
    fun `backtick and double-quoted identifiers are colored as string`() = runTest {
        assertEquals(testColors.string, highlight("`col`").colorAt(0))
        assertEquals(testColors.string, highlight("\"col\"").colorAt(0))
    }

    @Test
    fun `keywords are case-insensitive`() = runTest {
        for (keyword in listOf("SELECT", "select", "Select", "SeLeCt")) {
            assertEquals(testColors.keyword, highlight(keyword).colorAt(0), "'$keyword' should be colored as keyword")
        }
    }

    @Test
    fun `keywords are colored as keyword`() = runTest {
        val keywords =
            listOf("SELECT", "FROM", "WHERE", "INSERT", "UPDATE", "DELETE", "CREATE", "JOIN", "GROUP BY", "VALUES")
        for (keyword in keywords) {
            assertEquals(testColors.keyword, highlight(keyword).colorAt(0), "'$keyword' should be colored as keyword")
        }
    }

    @Test
    fun `alias and order keywords are colored as keyword`() = runTest {
        assertEquals(testColors.keyword, highlight("AS").colorAt(0))
        assertEquals(testColors.keyword, highlight("DESC").colorAt(0))
    }

    @Test
    fun `storage modifiers are colored as keyword`() = runTest {
        val result = highlight("PRIMARY KEY")
        assertEquals(testColors.keyword, result.colorAt(0))
        assertEquals(testColors.keyword, result.colorAt(8))
    }

    @Test
    fun `null is a keyword, not a constant`() = runTest {
        // The bundle scopes null as keyword.other.DDL.create.II.sql; it has no constant.language rule at all
        assertEquals(testColors.keyword, highlight("NULL").colorAt(0))
        assertEquals(testColors.keyword, highlight("null").colorAt(0))
    }

    @Test
    fun `true and false are not styled`() = runTest {
        // Neither word appears anywhere in the bundle
        assertNull(highlight("TRUE").colorAt(0))
        assertNull(highlight("false").colorAt(0))
    }

    @Test
    fun `qualified names are colored as constant`() = runTest {
        // constant.other.database-name.sql and constant.other.table-name.sql
        val result = highlight("myschema.mytable")
        assertEquals(testColors.constant, result.colorAt(0))
        assertEquals(testColors.constant, result.colorAt(9))
    }

    @Test
    fun `operators are colored as operator`() = runTest {
        assertEquals(testColors.operator, highlight("SELECT * FROM t").colorAt(7))
        assertEquals(testColors.operator, highlight("x = 1").colorAt(2))
        assertEquals(testColors.operator, highlight("a || b").colorAt(2))
    }

    @Test
    fun `types are colored as keyword`() = runTest {
        // storage.type.sql maps to IntelliJ's keyword key
        for (type in listOf("INT", "INTEGER", "varchar", "VARCHAR2", "TIMESTAMP", "boolean")) {
            assertEquals(testColors.keyword, highlight(type).colorAt(0), "'$type' should be a keyword")
        }
    }

    @Test
    fun `type length argument is colored as number`() = runTest {
        val result = highlight("varchar(10)")
        assertEquals(testColors.keyword, result.colorAt(0))
        assertEquals(testColors.number, result.colorAt(8))
    }

    @Test
    fun `types the bundle does not know are not styled`() = runTest {
        // uuid is a PostgreSQL type; this bundle is the MSSQL one and never mentions it
        assertNull(highlight("uuid").colorAt(0))
    }

    @Test
    fun `known function names are colored as function call`() = runTest {
        assertEquals(testColors.functionCall, highlight("COUNT(*)").colorAt(0))
        assertEquals(testColors.functionCall, highlight("abs(1)").colorAt(0))
    }

    @Test
    fun `keywords before parenthesis are not colored as function calls`() = runTest {
        assertEquals(testColors.keyword, highlight("IN (1, 2)").colorAt(0))
    }

    @Test
    fun `integers are colored as number`() = runTest { assertEquals(testColors.number, highlight("42").colorAt(0)) }

    @Test
    fun `only the integer parts of a decimal are colored as number`() = runTest {
        // The bundle's only standalone numeric rule is \b\d+\b, so 3.14 is two numbers with a bare dot between
        val result = highlight("3.14")
        assertEquals(testColors.number, result.colorAt(0))
        assertNull(result.colorAt(1))
        assertEquals(testColors.number, result.colorAt(2))
    }

    @Test fun `exponent notation is not recognized as a number`() = runTest { assertNull(highlight("1e10").colorAt(0)) }

    @Test
    fun `create statement colors the keywords and the created name`() = runTest {
        val result = highlight("CREATE TABLE foo")
        assertEquals(testColors.keyword, result.colorAt(0))
        assertEquals(testColors.keyword, result.colorAt(7))
        assertEquals(testColors.functionCall, result.colorAt(13))
    }

    @Test
    fun `drop statement colors only its keywords`() = runTest {
        // Two meta.drop rules match here; the bundle lists the one without the name capture first, and it wins
        val result = highlight("DROP TABLE users")
        assertEquals(testColors.keyword, result.colorAt(0))
        assertEquals(testColors.keyword, result.colorAt(5))
        assertNull(result.colorAt(11))
    }

    @Test
    fun `bracketed identifiers are not colored, not even reserved words`() = runTest {
        assertNull(highlight("[select]").colorAt(1))
    }

    @Test fun `variables are not colored`() = runTest { assertNull(highlight("@count").colorAt(1)) }
}

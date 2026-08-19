// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.networknt.wrapper

import com.intellij.json.JsonFileType
import com.intellij.json.psi.JsonFile
import com.intellij.json.json5.Json5FileType
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker
import org.assertj.core.api.Assertions.assertThat
import org.intellij.lang.annotations.Language
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.ArrayNode
import tools.jackson.databind.node.BigIntegerNode
import tools.jackson.databind.node.BooleanNode
import tools.jackson.databind.node.DecimalNode
import tools.jackson.databind.node.DoubleNode
import tools.jackson.databind.node.IntNode
import tools.jackson.databind.node.LongNode
import tools.jackson.databind.node.NullNode
import tools.jackson.databind.node.ObjectNode
import tools.jackson.databind.node.StringNode
import java.math.BigInteger

class PsiToJsonNodeConverterTest : BasePlatformTestCase() {

  private fun convertJson(@Language("JSON") json: String): JsonNode? {
    val psiFile = myFixture.configureByText(JsonFileType.INSTANCE, json) as JsonFile
    val rootElement = psiFile.topLevelValue ?: return null
    val walker = JsonLikePsiWalker.getWalker(rootElement) ?: return null
    return convertPsiToJsonNode(walker, rootElement)
  }

  // https://spec.json5.org/#numbers
  private fun convertJson5(@Language("JSON5") json: String): JsonNode? {
    val psiFile = myFixture.configureByText(Json5FileType.INSTANCE, json)
    val rootElement = psiFile.firstChild ?: return null
    val walker = JsonLikePsiWalker.getWalker(rootElement) ?: return null
    return convertPsiToJsonNode(walker, rootElement)
  }

  fun `test empty object`() {
    val node = convertJson("{}")
    assertThat(node).isInstanceOf(ObjectNode::class.java)
    assertThat(node!!.size()).isEqualTo(0)
  }

  fun `test empty array`() {
    val node = convertJson("[]")
    assertThat(node).isInstanceOf(ArrayNode::class.java)
    assertThat(node!!.size()).isEqualTo(0)
  }

  fun `test flat object with string property`() {
    val node = convertJson("""{"name": "Alice"}""")
    assertThat(node).isInstanceOf(ObjectNode::class.java)
    assertThat(node!!.get("name").asText()).isEqualTo("Alice")
    assertThat(node.get("name")).isInstanceOf(StringNode::class.java)
  }

  fun `test flat object with number property`() {
    val node = convertJson("""{"age": 30}""")
    assertThat(node).isInstanceOf(ObjectNode::class.java)
    assertThat(node!!.get("age").intValue()).isEqualTo(30)
    assertThat(node.get("age")).isInstanceOf(IntNode::class.java)
  }

  fun `test flat object with boolean property`() {
    val node = convertJson("""{"active": true}""")
    assertThat(node).isInstanceOf(ObjectNode::class.java)
    assertThat(node!!.get("active").booleanValue()).isTrue()
    assertThat(node.get("active")).isInstanceOf(BooleanNode::class.java)
  }

  fun `test flat object with null property`() {
    val node = convertJson("""{"value": null}""")
    assertThat(node).isInstanceOf(ObjectNode::class.java)
    assertThat(node!!.get("value").isNull).isTrue()
    assertThat(node.get("value")).isInstanceOf(NullNode::class.java)
  }

  fun `test flat object with mixed properties`() {
    val node = convertJson("""{"name": "Bob", "age": 25, "active": false, "data": null}""")
    assertThat(node).isInstanceOf(ObjectNode::class.java)
    assertThat(node!!.get("name").asText()).isEqualTo("Bob")
    assertThat(node.get("age").intValue()).isEqualTo(25)
    assertThat(node.get("active").booleanValue()).isFalse()
    assertThat(node.get("data").isNull).isTrue()
  }

  fun `test integer parsing small`() {
    val node = convertJson("5")
    assertThat(node).isInstanceOf(IntNode::class.java)
    assertThat(node!!.intValue()).isEqualTo(5)
  }

  fun `test integer parsing large`() {
    val node = convertJson("999999999")
    assertThat(node).isInstanceOf(IntNode::class.java)
    assertThat(node!!.intValue()).isEqualTo(999999999)
  }

  fun `test integer parsing negative`() {
    val node = convertJson("-42")
    assertThat(node).isInstanceOf(IntNode::class.java)
    assertThat(node!!.intValue()).isEqualTo(-42)
  }

  fun `test long integer parsing`() {
    val node = convertJson("9999999999")
    assertThat(node).isInstanceOf(LongNode::class.java)
    assertThat(node!!.longValue()).isEqualTo(9999999999L)
  }

  fun `test integer parsing beyond long range`() {
    val value = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.valueOf(123L))
    val node = convertJson(value.toString())
    assertThat(node).isInstanceOf(BigIntegerNode::class.java)
    assertThat(node!!.bigIntegerValue()).isEqualTo(value)
  }

  // https://spec.json5.org/#numbers
  fun `test JSON5 hexadecimal integer parsing`() {
    val node = convertJson5("0x10")
    assertThat(node).isInstanceOf(IntNode::class.java)
    assertThat(node!!.intValue()).isEqualTo(16)
  }

  // https://spec.json5.org/#numbers
  // Regression guard: hex digit 'E'/'e' was misdetected as a decimal exponent marker,
  // sending the literal into BigDecimal(text) which cannot parse hex and threw NumberFormatException.
  fun `test JSON5 hexadecimal integer parsing with hex digit e`() {
    val node = convertJson5("0x1E")
    assertThat(node).isInstanceOf(IntNode::class.java)
    assertThat(node!!.intValue()).isEqualTo(30)
  }

  // https://spec.json5.org/#numbers
  fun `test JSON5 hexadecimal integer parsing all hex letters`() {
    val node = convertJson5("0xdead")
    assertThat(node).isInstanceOf(IntNode::class.java)
    assertThat(node!!.intValue()).isEqualTo(0xdead)
  }

  // https://spec.json5.org/#strings
  fun `test JSON5 hexadecimal string escape is decoded`() {
    val node = convertJson5("'\\x41'")
    assertThat(node).isInstanceOf(StringNode::class.java)
    assertThat(node!!.asText()).isEqualTo("A")
  }

  // https://spec.json5.org/#strings
  fun `test JSON5 vertical tab string escape is decoded`() {
    val node = convertJson5("'a\\vb'")
    assertThat(node).isInstanceOf(StringNode::class.java)
    assertThat(node!!.asText()).isEqualTo("a\u000Bb")
  }

  // https://spec.json5.org/#strings
  fun `test JSON5 escaped apostrophe is decoded`() {
    val node = convertJson5("'it\\'s'")
    assertThat(node).isInstanceOf(StringNode::class.java)
    assertThat(node!!.asText()).isEqualTo("it's")
  }

  // https://spec.json5.org/#strings
  fun `test JSON5 identity string escape is decoded`() {
    val node = convertJson5("'\\a'")
    assertThat(node).isInstanceOf(StringNode::class.java)
    assertThat(node!!.asText()).isEqualTo("a")
  }

  // https://spec.json5.org/#strings
  fun `test JSON5 unicode line separator continuation is removed`() {
    val node = convertJson5("'first\\" + "\u2028" + "second'")
    assertThat(node).isInstanceOf(StringNode::class.java)
    assertThat(node!!.asText()).isEqualTo("firstsecond")
  }

  // https://spec.json5.org/#strings
  fun `test JSON5 paragraph separator continuation is removed`() {
    val node = convertJson5("'first\\" + "\u2029" + "second'")
    assertThat(node).isInstanceOf(StringNode::class.java)
    assertThat(node!!.asText()).isEqualTo("firstsecond")
  }

  // https://spec.json5.org/#numbers
  fun `test JSON5 Infinity becomes a positive infinite number`() {
    val node = convertJson5("{value: Infinity}")
    assertThat(node).isInstanceOf(ObjectNode::class.java)
    assertThat(node!!.get("value")).isInstanceOf(DoubleNode::class.java)
    assertThat(node.get("value").doubleValue()).isEqualTo(Double.POSITIVE_INFINITY)
  }

  // https://spec.json5.org/#numbers
  fun `test JSON5 negative Infinity becomes a negative infinite number`() {
    val node = convertJson5("-Infinity")
    assertThat(node).isInstanceOf(DoubleNode::class.java)
    assertThat(node!!.doubleValue()).isEqualTo(Double.NEGATIVE_INFINITY)
  }

  // https://spec.json5.org/#numbers
  fun `test JSON5 NaN becomes NaN within array without aborting siblings`() {
    val node = convertJson5("[1, NaN, 2]")
    assertThat(node).isInstanceOf(ArrayNode::class.java)
    assertThat(node!!.size()).isEqualTo(3)
    assertThat(node.get(0).intValue()).isEqualTo(1)
    assertThat(node.get(1)).isInstanceOf(DoubleNode::class.java)
    assertThat(node.get(1).doubleValue()).isNaN()
    assertThat(node.get(2).intValue()).isEqualTo(2)
  }

  // https://www.rfc-editor.org/rfc/rfc8259#section-3
  fun `test JSON string TRUE remains a string`() {
    val node = convertJson("\"TRUE\"")
    assertThat(node).isInstanceOf(StringNode::class.java)
    assertThat(node!!.asText()).isEqualTo("TRUE")
  }

  // https://www.rfc-editor.org/rfc/rfc8259#section-3
  @Suppress("JsonStandardCompliance")
  fun `test uppercase JSON boolean is not parsed as boolean`() {
    val node = convertJson("TRUE")
    assertThat(node).isNotInstanceOf(BooleanNode::class.java)
  }

  // https://www.rfc-editor.org/rfc/rfc8259#section-7
  @Suppress("JsonStandardCompliance")
  fun `test single quoted JSON string is not converted`() {
    assertThat(convertJson("'it''s'")).isNull()
  }

  // https://www.rfc-editor.org/rfc/rfc8259#section-6
  fun `test hexadecimal JSON literal is not parsed as number`() {
    assertJsonDoesNotProduceNumber("0x10")
  }

  // https://www.rfc-editor.org/rfc/rfc8259#section-6
  fun `test binary JSON literal is not parsed as number`() {
    assertJsonDoesNotProduceNumber("0b10000")
  }

  // https://www.rfc-editor.org/rfc/rfc8259#section-6
  fun `test octal JSON literal is not parsed as number`() {
    assertJsonDoesNotProduceNumber("0o20")
  }

  // https://www.rfc-editor.org/rfc/rfc8259#section-6
  fun `test underscored JSON literal is not parsed as number`() {
    assertJsonDoesNotProduceNumber("1_000")
  }

  // https://www.rfc-editor.org/rfc/rfc8259#section-6
  fun `test signed JSON literal is not parsed as number`() {
    assertJsonDoesNotProduceNumber("+1")
  }

  // https://www.rfc-editor.org/rfc/rfc8259#section-6
  // https://spec.json5.org/#numbers
  fun `test JSON Infinity extension is not parsed as JSON number`() {
    assertJsonDoesNotProduceNumber("Infinity")
  }

  // https://www.rfc-editor.org/rfc/rfc8259#section-6
  // https://spec.json5.org/#numbers
  fun `test JSON NaN extension is not parsed as JSON number`() {
    assertJsonDoesNotProduceNumber("NaN")
  }

  private fun assertJsonDoesNotProduceNumber(json: String) {
    assertThat(convertJson(json)?.isNumber == true).isFalse()
  }

  fun `test float parsing positive`() {
    val node = convertJson("3.14")
    assertThat(node).isInstanceOf(DecimalNode::class.java)
    assertThat(node!!.doubleValue()).isEqualTo(3.14)
  }

  fun `test float parsing negative`() {
    val node = convertJson("-2.5")
    assertThat(node).isInstanceOf(DecimalNode::class.java)
    assertThat(node!!.doubleValue()).isEqualTo(-2.5)
  }

  fun `test float parsing scientific notation`() {
    val node = convertJson("1.5e10")
    assertThat(node).isInstanceOf(DecimalNode::class.java)
    assertThat(node!!.doubleValue()).isEqualTo(1.5e10)
  }

  fun `test string escape newline`() {
    val node = convertJson("""{"s": "hello\nworld"}""")
    assertThat(node!!.get("s").asText()).isEqualTo("hello\nworld")
  }

  fun `test string escape quote`() {
    val node = convertJson("""{"s": "say \"hi\""}""")
    assertThat(node!!.get("s").asText()).isEqualTo("say \"hi\"")
  }

  fun `test string escape backslash`() {
    val node = convertJson("""{"s": "path\\to\\file"}""")
    assertThat(node!!.get("s").asText()).isEqualTo("path\\to\\file")
  }

  fun `test string escape unicode`() {
    val node = convertJson("""{"s": "\u0041"}""")
    assertThat(node!!.get("s").asText()).isEqualTo("A")
  }

  fun `test string escape unicode non-ascii BMP`() {
    val node = convertJson("""{"s": "café"}""")
    assertThat(node!!.get("s").asText()).isEqualTo("café")
  }

  fun `test string escape unicode surrogate pair`() {
    val node = convertJson("""{"s": "😀"}""")
    assertThat(node!!.get("s").asText()).isEqualTo("😀")
  }

  fun `test string escape unicode at end of string`() {
    // Regression guard: previous hand-rolled unescape had an off-by-one
    // (i + 5 < s.length instead of <=) and dropped the trailing \uXXXX.
    val node = convertJson("""{"s": "prefixA"}""")
    assertThat(node!!.get("s").asText()).isEqualTo("prefixA")
  }

  fun `test string without escapes short-circuits`() {
    val node = convertJson("""{"s": "no escapes here"}""")
    assertThat(node!!.get("s").asText()).isEqualTo("no escapes here")
  }

  fun `test string empty`() {
    val node = convertJson("""{"s": ""}""")
    assertThat(node!!.get("s").asText()).isEqualTo("")
  }

  fun `test string escape tab and carriage return`() {
    val node = convertJson("""{"s": "a\tb\rc"}""")
    assertThat(node!!.get("s").asText()).isEqualTo("a\tb\rc")
  }

  fun `test string escape forward slash`() {
    val node = convertJson("""{"s": "a\/b"}""")
    assertThat(node!!.get("s").asText()).isEqualTo("a/b")
  }

  fun `test nested objects 3 levels`() {
    val node = convertJson("""{"a": {"b": {"c": "deep"}}}""")
    assertThat(node).isInstanceOf(ObjectNode::class.java)
    assertThat(node!!.get("a")).isInstanceOf(ObjectNode::class.java)
    assertThat(node.get("a").get("b")).isInstanceOf(ObjectNode::class.java)
    assertThat(node.get("a").get("b").get("c").asText()).isEqualTo("deep")
  }

  fun `test array of primitives`() {
    val node = convertJson("""[1, "two", true, null]""")
    assertThat(node).isInstanceOf(ArrayNode::class.java)
    assertThat(node!!.size()).isEqualTo(4)
    assertThat(node.get(0)).isInstanceOf(IntNode::class.java)
    assertThat(node.get(1)).isInstanceOf(StringNode::class.java)
    assertThat(node.get(2)).isInstanceOf(BooleanNode::class.java)
    assertThat(node.get(3)).isInstanceOf(NullNode::class.java)
  }

  fun `test array of objects`() {
    val node = convertJson("""[{"name": "a"}, {"name": "b"}]""")
    assertThat(node).isInstanceOf(ArrayNode::class.java)
    assertThat(node!!.size()).isEqualTo(2)
    assertThat(node.get(0).get("name").asText()).isEqualTo("a")
    assertThat(node.get(1).get("name").asText()).isEqualTo("b")
  }

  fun `test deep nesting 5 levels`() {
    val node = convertJson("""{"a": {"b": {"c": {"d": {"e": 42}}}}}""")
    assertThat(node!!.get("a").get("b").get("c").get("d").get("e").intValue()).isEqualTo(42)
  }

  fun `test null walker returns null`() {
    val result = convertPsiToJsonNode(null, null)
    assertThat(result).isNull()
  }

  fun `test null root element returns null`() {
    val psiFile = myFixture.configureByText(JsonFileType.INSTANCE, "{}") as JsonFile
    val rootElement = psiFile.topLevelValue!!
    val walker = JsonLikePsiWalker.getWalker(rootElement)!!
    val result = convertPsiToJsonNode(walker, null)
    assertThat(result).isNull()
  }

  fun `test empty string property name`() {
    val node = convertJson("""{"": "empty key"}""")
    assertThat(node).isInstanceOf(ObjectNode::class.java)
    assertThat(node!!.get("").asText()).isEqualTo("empty key")
  }

  fun `test boolean false`() {
    val node = convertJson("false")
    assertThat(node).isInstanceOf(BooleanNode::class.java)
    assertThat(node!!.booleanValue()).isFalse()
  }

  fun `test boolean true`() {
    val node = convertJson("true")
    assertThat(node).isInstanceOf(BooleanNode::class.java)
    assertThat(node!!.booleanValue()).isTrue()
  }

  fun `test null literal`() {
    val node = convertJson("null")
    assertThat(node).isInstanceOf(NullNode::class.java)
  }

  fun `test string literal`() {
    val node = convertJson(""""hello"""")
    assertThat(node).isInstanceOf(StringNode::class.java)
    assertThat(node!!.asText()).isEqualTo("hello")
  }

  fun `test complex nested structure`() {
    val node = convertJson("""
      {
        "users": [
          {"name": "Alice", "tags": ["admin", "user"], "age": 30},
          {"name": "Bob", "tags": ["user"], "active": false}
        ],
        "count": 2,
        "meta": null
      }
    """.trimIndent())
    assertThat(node).isInstanceOf(ObjectNode::class.java)
    assertThat(node!!.get("count").intValue()).isEqualTo(2)
    assertThat(node.get("meta").isNull).isTrue()

    val users = node.get("users")
    assertThat(users).isInstanceOf(ArrayNode::class.java)
    assertThat(users.size()).isEqualTo(2)
    assertThat(users.get(0).get("tags").get(0).asText()).isEqualTo("admin")
    assertThat(users.get(1).get("active").booleanValue()).isFalse()
  }
}

// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jsonpath

// Behavior compatible with com.jayway.jsonpath
class JsonPathJaywayParserTest : JsonPathParsingTestCase("jayway") {
  fun testProgrammaticFilterPlaceholder() {
    doCodeTest("\$.programmatic_filter[?]") // ? will be replaced by programmatic filter at runtime
  }

  fun testFunctionWithArgumentExpression() {
    doCodeTest("\$.sum(\$..timestamp)")
  }

  fun testFunctionWithMultipleArgs() {
    doCodeTest("\$.max(\$..timestamp.avg(), 100)")
  }

  fun testFunctionOnTopLevel() {
    doCodeTest("concat(\"/\", \$.key)")
  }

  fun testFilterWithComparison() {
    doCodeTest("\$.batches.results[?(@.values.length() >= \$.batches.minBatchSize)].values.avg()")
  }

  fun testNestedFunctionCall() {
    doCodeTest("\$.avg(\$.numbers.min(), \$.numbers.max())")
  }

  fun testFunctionLiteralArg() {
    doCodeTest("\$.sum(50)")
  }

  fun testStringLiteralsConcat() {
    doCodeTest("\$.text.concat(\"-\", \"some\")")
  }

  fun testRegexMatch() {
    doCodeTest("\$.text[?(@ =~ /9.*9/)]")
  }

  fun testRegexCaseInsensitive() {
    doCodeTest("\$.data_text[?(@ =~ /test/i)]")
  }

  fun testRegexWithSlashes() {
    doCodeTest("\$.regex[?(@ =~ /^\\w+\$/U)]")
  }

  fun testRegexWithEscapedSlash() {
    doCodeTest("\$[?(@ =~ /\\/|x/)]")
  }

  fun testMultipleQuotedPaths() {
    doCodeTest("\$['a', 'x']")
  }

  fun testMixedSegmentTypes() {
    doCodeTest("\$['a', 'c'].v")
  }

  fun testDotBeforeQuotedSegment() {
    doCodeTest("\$.['store'].['book'][0]")
  }

  fun testQuotedSegmentWithFilter() {
    doCodeTest("\$['a', 'c'][?(@.flag)].v")
  }

  fun testQuotedSegmentInTheMiddle() {
    doCodeTest("\$.x[*]['a', 'c'].v")
  }

  fun testDashInsideIdentifier() {
    doCodeTest("\$.int-max-property")
  }

  fun testAndOperator() {
    doCodeTest("\$.text[?(@.name == 'name' && $.length() > 2)]")
  }

  fun testOrOperator() {
    doCodeTest("\$.text[?(@.name == 'name' || $.length() < 2)]")
  }

  fun testSubsequentArrayIndexes() {
    doCodeTest("\$..[2][3]")
  }

  fun testScanWithFilter() {
    doCodeTest("\$..*[?(@.length() > 5)]")
  }

  fun testMultiPropsInTheMiddle() {
    doCodeTest("\$[*][*]['a', 'c'].v")
  }

  fun testInOperatorUpperCase() {
    doCodeTest("$[?(@.a IN @.b)]")
  }

  fun testInOperatorLowerCase() {
    doCodeTest("$[?('demo' in @.array)]")
  }

  fun testEmptyOperatorWithConstant() {
    doCodeTest("$[?(@.array empty true)]")
  }

  fun testEmptyOperatorWithExpression() {
    doCodeTest("$[?(@.array empty @.flag)]")
  }

  fun testArrayLiterals() {
    doCodeTest("\$.store.bicycle[?(@.gears == [23, 50])]")
  }

  fun testArrayLiteralsFromBothSides() {
    doCodeTest("\$.bicycle[?([1, 2] contains [1])]")
  }

  fun testObjectLiterals() {
    doCodeTest("\$.store.bicycle[?(@.extra == {\"x\":0})]")
  }

  fun testComplexObjectLiterals() {
    doCodeTest("\$.store.bicycle[?(@.extra == { 'x': [{}, {'key' : 'value'}] })]")
  }

  fun testEqOperators() {
    doCodeTest("@.demo[?(@.a == 1 && @.b === 'x')]")
  }

  fun testNeOperators() {
    doCodeTest("@.demo[?(@.a != 2 && @.b !== 'x')]")
  }

  fun testIndexWithoutContext() {
    doCodeTest("[0]")
  }

  fun testWildcardIndex() {
    doCodeTest("*[0]")
  }

  fun testQuotedPathAfterIndex() {
    doCodeTest("[0][\"id\"]")
  }

  fun testDotAfterIndex() {
    doCodeTest("[0].id")
  }

  fun testRepeatedWildcard() {
    doCodeTest("**")
  }

  fun testRepeatedWildcardInTheMiddle() {
    doCodeTest("[1].**.created")
  }
}
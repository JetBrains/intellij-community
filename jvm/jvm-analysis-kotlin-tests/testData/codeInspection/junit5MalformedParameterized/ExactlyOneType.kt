package test

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

object PrivateRule {
  @ParameterizedTest
  @<warning descr="Exactly one type of input must be provided">ValueSource</warning>(ints = [1], strings = ["str"])
  fun testWithMultipleValues() {
  }

  @ParameterizedTest
  @<warning descr="No value source is defined">ValueSource</warning>()
  fun testWithNoValues() {
  }
}
package test

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

object PrivateClassRule {
  @ParameterizedTest
  @MethodSource("<warning descr="Cannot resolve target method source: 'unknown'">unknown</warning>")
  fun unknownMethodSource() {
  }
}
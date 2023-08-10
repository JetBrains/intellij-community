package test

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
object PrivateRule {
  @ParameterizedTest
  @MethodSource("<warning descr="Method source 'squares' must have one of the following return types: 'Stream<?>', 'Iterator<?>', 'Iterable<?>' or 'Object[]'">squares</warning>")
  fun foo() {}


  fun squares(): String {
    return "aa"
  }
}
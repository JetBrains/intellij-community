package test

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class NoParamsClazz {
  @ParameterizedTest
  @MethodSource("<warning descr="Method source 'b' should have no parameters">b</warning>")
  fun testWithParams() {
  }

  companion object {
    @kotlin.jvm.JvmStatic
    fun b(<warning descr="[UNUSED_PARAMETER] Parameter 'i' is never used">i</warning>: Int): Array<String> {
      return arrayOf("a", "b")
    }
  }
}
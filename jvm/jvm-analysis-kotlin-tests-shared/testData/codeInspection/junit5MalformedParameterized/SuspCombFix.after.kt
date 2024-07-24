package test

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource

object PrivateClassRule {
  @MethodSource("parmeters")
  @Test
  fun testWithTestAnnotation() {}



  @ValueSource()
  @Test
  fun testWithTestAnnotationNoParameterized() {}
}
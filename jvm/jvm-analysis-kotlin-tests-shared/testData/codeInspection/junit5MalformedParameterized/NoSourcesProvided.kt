package test

object PrivateRule {
  @<warning descr="No sources are provided, the suite would be empty">ParameterizedTest</warning>
  fun testWithParamsNoSource() {}
}
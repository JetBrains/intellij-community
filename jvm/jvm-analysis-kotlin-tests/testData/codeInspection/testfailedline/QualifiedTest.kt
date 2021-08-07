class QualifiedTest : junit.framework.TestCase() {
  fun testFoo() {
    Assertions.<warning descr="junit.framework.AssertionFailedError:">assertEquals</warning>()
  }

  object Assertions {
    fun assertEquals() {}
  }
}
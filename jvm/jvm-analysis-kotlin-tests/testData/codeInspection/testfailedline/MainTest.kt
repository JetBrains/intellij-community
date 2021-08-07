class MainTest : junit.framework.TestCase() {
  fun testFoo() {
    <warning descr="junit.framework.AssertionFailedError:">assertEquals</warning>()
    assertEquals()
  }

  fun assertEquals() {}
}
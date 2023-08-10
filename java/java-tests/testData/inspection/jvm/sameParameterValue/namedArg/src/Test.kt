class TestDifferent {
  private fun foo(arg1: String, arg2: String) { }

  fun bar() {
    foo("foo", "bar")
    foo(arg2 = "foo", arg1 = "bar")
  }
}

class TheSame {
  private fun foo(arg1: String, arg2: String) { }

  fun bar() {
    foo("foo", "bar")
    foo(arg1 = "foo", arg2 = "bar")
    foo(arg2 = "bar", arg1 = "foo")
  }
}

fun main(args: Array<String>) {
  TestDifferent().bar()
  TheSame().bar()
}
class TestDifferent {
  private fun foo(vararg args: String) { }

  fun bar() {
    foo("foo", "bar")
    foo("foobar")
  }
}

class TheSame {
  private fun foo(vararg args: String) { }

  fun bar() {
    foo("foo")
    foo("foo")
  }
}

class TheSameMultipleArgs {
  private fun foo(vararg args: String) { }

  fun bar() {
    foo("foo", "bar")
    foo("foo", "bar")
  }
}

class TheSameVarargIsFirst {
  private fun foo(vararg args: String, args1: String? = null) { }

  fun bar() {
    foo("foo", "bar")
    foo("foo", "bar")
    foo("foo", "bar", args1 = "foobar")
    //foo(args1 = "foobar", args = arrayOf("foo", "bar")) // named varargs not supported because argument is array call here
  }
}

class TheSameVarargIsLast {
  private fun foo(args1: String, vararg args: String) { }

  fun bar() {
    foo("foo", "bar", "foobar")
    foo("bar", "bar", "foobar")
    foo(args1 = "bar", "bar", "foobar")
  }
}

class TheSameVarargIsMiddle {
  private fun foo(args1: String, vararg args: String, args2: String) { }

  fun bar() {
    foo("barfoo", "foo", "bar", args2 = "foobar")
    foo(args1 = "foobar", "foo", "bar", args2 = "barfoo")
  }
}

fun main(args: Array<String>) {
  TestDifferent().bar()
  TheSame().bar()
  TheSameMultipleArgs().bar()
  TheSameVarargIsLast().bar()
}
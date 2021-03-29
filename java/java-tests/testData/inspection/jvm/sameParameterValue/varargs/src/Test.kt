class Test {
  private fun foo(vararg args: String) {}

  fun bar() {
    foo("foo", "bar")
    foo("bla")
  }
}

object AnotherDiiferentVarargs {
  val TEXT = "text"
  val ANOTHER_TEXT = "another text"

  fun printString(input: String, vararg attrs: String) {
    System.out.println(input)
    for (string in attrs) {
      System.out.println(string)
    }
  }
}

object ExtensionFunctionVarargs {
  private fun String.foo(i: Int, vararg args: String) {}

  fun bar() {
    "one".foo(1, "baz")
    "two".foo(1, "foo", "bar")
  }
}

object FunctionNonLastVarargsParameter {
  private fun foo(vararg args: String, str: String) {}

  fun bar() {
    foo("foo", "bar", str = "one")
    foo("bar", "foo", str = "two")
  }
}

fun main(args: Array<String>) {
  Test().bar()
  AnotherDiiferentVarargs.printString(AnotherDiiferentVarargs.TEXT, "optional")
  AnotherDiiferentVarargs.printString(AnotherDiiferentVarargs.ANOTHER_TEXT)
  ExtensionFunctionVarargs.bar()
  ExtensionFunctionNonLastVarargsParameter.bar()
}
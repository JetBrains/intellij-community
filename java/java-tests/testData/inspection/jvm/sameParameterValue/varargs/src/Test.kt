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

class TheSame {
  private fun foo(vararg args: String) {}

  fun bar() {
    foo("foo")
    foo("foo")
  }
}

fun main(args: Array<String>) {
  Test().bar()
  AnotherDiiferentVarargs.printString(AnotherDiiferentVarargs.TEXT, "optional")
  AnotherDiiferentVarargs.printString(AnotherDiiferentVarargs.ANOTHER_TEXT)
  TheSame().bar()
}
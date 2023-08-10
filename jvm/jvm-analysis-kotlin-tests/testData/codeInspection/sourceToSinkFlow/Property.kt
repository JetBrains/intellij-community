import org.checkerframework.checker.tainting.qual.*

class TestCtor {

  var f: String = ""

  fun test() {
    f = bar()
    sink(<caret>f)
  }

  fun bar(): String {
    return "foo"
  }

  fun sink(s: @Untainted String) {

  }
}
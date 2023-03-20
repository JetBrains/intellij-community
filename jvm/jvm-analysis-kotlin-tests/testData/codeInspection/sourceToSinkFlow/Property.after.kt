import org.checkerframework.checker.tainting.qual.*

class TestCtor {

  var f: @Untainted String = ""

  fun test() {
    f = bar()
    sink(f)
  }

  fun bar(): @Untainted String {
    return "foo"
  }

  fun sink(s: @Untainted String) {

  }
}
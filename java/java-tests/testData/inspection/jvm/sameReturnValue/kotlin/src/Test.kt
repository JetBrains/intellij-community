class Test {
  fun xxx(): Int {
    return 0
  }
}

open class A {
  open fun xxx(): String {
    if (1 > 10) {
      return "foo"
    } else {
      return "foo"
    }
  }
}

class B:A() {
  override fun xxx(): String {
    return "foo"
  }
}


fun main(args: Array<String>) {
  println(Test().xxx())
  println(B().xxx())
  println(A().xxx())
}
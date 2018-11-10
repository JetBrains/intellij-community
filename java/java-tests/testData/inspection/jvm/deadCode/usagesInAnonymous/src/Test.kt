object MyClass {

  fun method(): String {
    return ""
  }

  open class Parent constructor(o: Any) {
    init {
      println(o)
    }
  }
}

fun main(args: Array<String>) {
  object : MyClass.Parent(MyClass.method()) {

  }
}
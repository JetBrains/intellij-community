fun main(args: Array<String>) {
  val child = ChildKt()
  child.someString = "World"
}

interface ParentKt {
  var someString: String
}

class ChildKt : ParentKt {
  override var someString = "Hello"
}

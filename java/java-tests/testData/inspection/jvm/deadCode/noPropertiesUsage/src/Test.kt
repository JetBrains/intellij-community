fun main(args: Array<String>) {
  val parentList = listOf(ChildKt())
  for (parent in parentList) {
  }
}

interface ParentKt {
  var someString: String
}

class ChildKt : ParentKt {
  override var someString = "Hello"
}

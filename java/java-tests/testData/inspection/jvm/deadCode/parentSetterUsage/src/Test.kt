fun main(args: Array<String>) {
  for (parent in getList()) {
    parent.someString = "World"
  }
}

fun getList(): List<ParentKt> = listOf(ChildKt())

interface ParentKt {
  var someString: String
}

class ChildKt : ParentKt {
  override var someString = "Hello"
}

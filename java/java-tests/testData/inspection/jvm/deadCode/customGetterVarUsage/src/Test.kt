fun main(args: Array<String>) {
  for (parent in getList()) {
    println(parent.someString)
  }
}

fun getList(): List<ParentKt> {
  return listOf(ChildKt())
}

interface ParentKt {
  val someString: String
}

class ChildKt : ParentKt  {
  override var someString = "Hello"

  get() = "$field World"
}


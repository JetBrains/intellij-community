fun main(args: Array<String>) {
  for (parent in getList()) {
    println(parent.someString)
  }
}

fun getList(): List<ParentKt> = listOf(ChildKt())

interface ParentKt {
  val someString: String
}

class ChildKt : ParentKt  {
  override val someString = "Hello"

  get() = "$field World"
}


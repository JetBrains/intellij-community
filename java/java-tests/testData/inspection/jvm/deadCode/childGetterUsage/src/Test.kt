fun main(args: Array<String>) {
  println(ChildKt().someString)
}

interface ParentKt {
  var someString: String
}

class ChildKt : ParentKt  {
  override var someString = "Hello"
}


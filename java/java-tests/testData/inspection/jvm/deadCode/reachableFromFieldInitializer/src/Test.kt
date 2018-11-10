class Test : Interface {
  var myField = init()

  fun init(): Int {
    return 1
  }

  override fun getField(): Int {
    return myField
  }
}

fun main(args: Array<String>) {
  val i: Interface = Test()
  println(i.field)
}
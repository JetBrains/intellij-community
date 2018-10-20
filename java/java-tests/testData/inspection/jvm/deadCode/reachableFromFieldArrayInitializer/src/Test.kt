class Test : Interface {

  override fun getField(): IntArray {
    return myField
  }

  companion object {
    var myField = intArrayOf(init())

    fun init(): Int {
      return 1
    }
  }
}

fun main(args: Array<String>) {
  val i: Interface = Test()
  println(i.field)
}

open class K(x: String) {

  init {
    println("initializer block that prints $x")
  }
}

class YYY : K(S) {
  companion object {
    const val S = "S"
    const val T = "T"
  }
}

fun main(args: Array<String>) {
  val x = YYY()
  println(x)
}
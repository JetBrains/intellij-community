class Test(param: String) {

  init {
    println("This is used!")
  }
}

fun main(args: Array<String>) {
  Test::class.java.getDeclaredConstructor(String::class.java).newInstance("Foo")
}

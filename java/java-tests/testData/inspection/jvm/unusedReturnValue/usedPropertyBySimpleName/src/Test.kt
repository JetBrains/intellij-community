class Test {
  protected val fileName : String get() = "fN"
  protected val fileName1: String get() = "fN1"
  
  fun printFileName1() {
    println(fileName1)
  }
  
}
fun main(args: Array<String>) {
  println(Test().fileName)
  println(Test().printFileName1())
}
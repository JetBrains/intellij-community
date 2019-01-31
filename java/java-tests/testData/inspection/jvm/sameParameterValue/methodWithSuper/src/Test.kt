internal class AAA {
  fun mmm(i: Int) {
    System.out.println(i + 10)
  }
}

internal class BBB : AAA() {
  @Override
  override fun mmm(i: Int) {
    System.out.println(i + 20)
  }
}

fun main(args: Array<String>) {
  BBB().mmm(10)
  AAA().mmm(10)
}
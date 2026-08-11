class Alpha {
  fun makeBeta(): Beta = Beta()
}

class Beta {
  fun makeAlpha(): Alpha = Alpha()
}

fun main() {
  println(Alpha().makeBeta())
  println(Beta().makeAlpha())
}

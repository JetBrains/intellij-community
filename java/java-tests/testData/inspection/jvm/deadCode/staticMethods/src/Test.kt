object A {
  fun xxx() {
    yyy()
  }
}

fun yyy() {
  A.xxx()
  B()
}

class B
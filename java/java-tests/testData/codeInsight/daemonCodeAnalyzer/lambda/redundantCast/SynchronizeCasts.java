class MyTest {

  void dblCast() {
    synchronized ((Runnable) (<warning descr="Casting '() -> {...}' to 'Runnable' is redundant">Runnable</warning>) () -> {}) {}
    synchronized ((Runnable) (<warning descr="Casting '(Runnable)() -> {...}' to 'Runnable' is redundant">Runnable</warning>)(<warning descr="Casting '() -> {...}' to 'Runnable' is redundant">Runnable</warning>) () -> {}) {}
  }
}

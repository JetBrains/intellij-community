// "Create missing branches 'A', 'B', and 'C'" "true"
class Main {
  enum E {A, B, C, C}

  public void f(E e) {
    switch<caret> (e) {
    }
  }
}
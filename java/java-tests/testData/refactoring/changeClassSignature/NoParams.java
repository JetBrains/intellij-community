class <caret>C {
}

class Usage extends C {
  {
    C c = new C();

    C c = new C() { }
  }
}
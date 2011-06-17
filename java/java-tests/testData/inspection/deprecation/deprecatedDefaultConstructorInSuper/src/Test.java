class C {
  @Deprecated C() { }
}

class D extends C {
}

class P {
  public static void main(String[] args) {
    new C(){};
  }
}
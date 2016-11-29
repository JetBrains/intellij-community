class C {
  @Deprecated C() { }
}

class P {
  public static void main(String[] args) {
    @SuppressWarnings("deprecation")
    C anonInnerface = new C() {};
  }
}
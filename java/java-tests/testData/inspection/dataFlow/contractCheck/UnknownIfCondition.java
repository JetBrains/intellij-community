class Test {
  
  @org.jetbrains.annotations.Contract("null->false")
  public static boolean smth(Object context) {
    if (someMethodWithUnknownContract(context)) {
      return true;
    }
    return false;
  }

  private static native boolean someMethodWithUnknownContract(Object o);

  private volatile boolean field;
  @org.jetbrains.annotations.Contract("->fail")
  void fail() {
    if (field) {
    }
    throw new AssertionError();
  }

}
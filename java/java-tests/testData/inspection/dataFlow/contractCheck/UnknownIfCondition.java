class Test {
  
  @org.jetbrains.annotations.Contract("null->false")
  public static boolean smth(Object context) {
    if (someMethodWithUnknownContract(context)) {
      return true;
    }
    return false;
  }

  private static native boolean someMethodWithUnknownContract(Object o);
}
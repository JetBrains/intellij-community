class CrashTestDummy {
  public CrashTestDummy(String bar<caret>) { // inline the parameter
    System.out.println(bar);
    super();
  }

  public static void main(String[] args) {
    new CrashTestDummy("bar".toString());
  }
}
class Test {
  public static final Test INSTANCE = new Test();

  public Test() {
    if (INSTANCE == null) {
      System.out.println("Instance is null");
    }
  }

  public static void main(String[] args) {
    System.out.println("Hello, world!");
  }
}
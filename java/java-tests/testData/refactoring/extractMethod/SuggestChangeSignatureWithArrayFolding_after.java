class Test {
  public static void main(String[] args, int i) {
      newMethod("hi");
      newMethod(args[i]);
  }

    private static void newMethod(String hi) {
        System.out.println(hi);
    }
}
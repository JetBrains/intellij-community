class Test {
  public static void main(String[] args, int i) {
      newMethod("hi");
      newMethod("world, " + args[i]);
  }

    private static void newMethod(String x) {
        System.out.println(x);
    }
}
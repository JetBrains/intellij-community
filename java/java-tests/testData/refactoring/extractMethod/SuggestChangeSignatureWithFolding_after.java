class Test {
  public static void main(String[] args, int i) {
      newMethod("hi");
      newMethod("world, " + args[i]);
  }

    private static void newMethod(String s) {
        System.out.println(s);
    }
}
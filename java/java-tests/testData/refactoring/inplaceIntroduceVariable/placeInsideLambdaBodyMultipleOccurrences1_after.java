class Test {
  {
    Runnable r = () -> {
        String expr = "";
        System.out.println(expr + expr);
    };
  }
}
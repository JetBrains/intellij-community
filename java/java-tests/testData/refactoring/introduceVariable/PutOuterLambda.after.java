class Foo {
  {
      String s = "Hello";
      System.out.println(s);
    Runnable r = () -> {
      System.out.println(s);
    };
  }
}
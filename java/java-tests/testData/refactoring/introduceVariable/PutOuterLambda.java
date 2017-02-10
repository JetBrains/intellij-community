class Foo {
  {
    System.out.println("Hello");
    Runnable r = () -> {
      System.out.println(<selection>"Hello"</selection>);
    };
  }
}
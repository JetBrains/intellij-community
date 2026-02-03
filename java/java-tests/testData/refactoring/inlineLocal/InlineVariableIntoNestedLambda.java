class Test {
  {
    String <caret>s = "hello";
    Runnable r = () -> {Runnable rr = () -> System.out.println(s);};
  }
}
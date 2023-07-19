class Main {

  public enum En {
    HHHH, HE, LI, BE, B, C, N, O, F, NE
  }
  public void foo(Object o) {
    switch (o) {
      case String s -> System.out.println(s);
      case Integer i -> System.out.println(i);
      case En.HH<caret> -> System.out.println("En -> H");
      case null, default -> System.out.println("null, default");
    }
  }}


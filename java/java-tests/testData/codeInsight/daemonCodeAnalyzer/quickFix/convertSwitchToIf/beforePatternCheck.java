// "Replace 'switch' with 'if'" "true-preview"
abstract class Test {
  abstract Object getObject();

  void foo(Object o) {
    <caret>switch (o) {
      case null, String s -> System.out.println("one");
      case ((Integer i) && (i > 0)) -> System.out.println("two");
      case /*1*/Float/*2*/ /*3*/f && f/*4*/ > 5 && f < 10 -> System.out.println("two");
      case Character c -> System.out.println(c);
      case Double c -> System.out.println();
      default -> {}
    }
  }
}
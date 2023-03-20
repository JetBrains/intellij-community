// "Replace 'switch' with 'if'" "true-preview"
abstract class Test {
  abstract Object getObject();

  void foo(Object o) {
      if (o == null || o instanceof String) {
          System.out.println("one");
      } else if (o instanceof Integer i && (i > 0)) {
          System.out.println("two");
      } else if (o instanceof  /*1*/ Float /*2*/ /*3*/f && f/*4*/ > 5 && f < 10) {
          System.out.println("two");
      } else if (o instanceof Character c) {
          System.out.println(c);
      } else if (o instanceof Double) {
          System.out.println();
      }
  }
}
import static I.*;

// "Replace Implements with Static Import" "true"
public class X {
  void foo() {
    System.out.println(FOO);
    System.out.println(FOO1);
    System.out.println(FOO2);
    System.out.println(FOO3);
    System.out.println(FOO4);
    System.out.println(FOO5);
  }
}

interface I {
  String FOO = "foo";
  String FOO1 = "foo";
  String FOO2 = "foo";
  String FOO3 = "foo";
  String FOO4 = "foo";
  String FOO5 = "foo";
}
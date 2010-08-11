import static I.FOO;

// "Replace Implements with Static Import" "true"
public class X {
  void foo() {
    System.out.println(FOO);
  }
}

interface I {
  String FOO = "foo";
}
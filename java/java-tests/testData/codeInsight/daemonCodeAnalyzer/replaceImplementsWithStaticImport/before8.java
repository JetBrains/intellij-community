// "Replace Implements with Static Import" "true"
public class X implements II {
  void bar() {
    System.out.println(FOO);
    System.out.println(BAZZ);
  }
}

interface I<caret>I extends I1{
  String FOO = "foo";
}

interface I1 {
  String BAZZ = "bazz";
}

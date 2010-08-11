import static I1.BAZZ;

// "Replace Implements with Static Import" "true"
public class X implements I {
  void bar() {
    System.out.println(FOO);
    System.out.println(BAZZ);
  }
}

interface I {
  String FOO = "foo";
}

interface I1 {
  String BAZZ = "bazz";
}

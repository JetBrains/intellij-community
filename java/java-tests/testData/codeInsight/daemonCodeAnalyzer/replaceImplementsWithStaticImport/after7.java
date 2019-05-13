import static I1.BAZZ;

// "Replace implements with static import" "true"
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

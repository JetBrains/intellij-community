import static I1.BAZZ;
import static II.FOO;

// "Replace implements with static import" "true"
public class X {
  void bar() {
    System.out.println(FOO);
    System.out.println(BAZZ);
  }
}

interface II extends I1{
  String FOO = "foo";
}

interface I1 {
  String BAZZ = "bazz";
}

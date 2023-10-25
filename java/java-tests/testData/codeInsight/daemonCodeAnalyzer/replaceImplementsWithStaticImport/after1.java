import static I.FOO;

// "Replace implements with static import" "true-preview"
public class X {
  void foo() {
    System.out.println(FOO);
  }
}

interface I {
  String FOO = "foo";
}
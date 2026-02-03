import java.util.Set;

class Test {

  public static void foo(String[] args, final Set<Test> singleton) {
    singleton.forEach(Test::m);
  }

  public static void m(Test...  others) {}
}
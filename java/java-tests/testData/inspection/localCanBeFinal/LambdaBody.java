import java.util.HashMap;

class Issue {
  public static void filterMap() {
    new HashMap<String, String>().forEach((s1, s2) -> {
      String <warning descr="Variable 'cc' can have 'final' modifier">cc</warning> = s1 + s2;
      System.err.println(cc);
    });
  }
}
final class Junk {
  public void sillyMethod() {
    Runnable <warning descr="Variable 'r' can have 'final' modifier">r</warning> = () -> {
      int <warning descr="Variable 'i' can have 'final' modifier">i</warning> = 0;
      System.out.println(i);
    };
  }
}



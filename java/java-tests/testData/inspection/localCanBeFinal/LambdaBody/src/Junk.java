import java.util.HashMap;

class Issue {
  public static void filterMap() {
    new HashMap<String, String>().forEach((s1, s2) -> {
      String cc = s1 + s2;
      System.err.println(cc);
    });
  }
}
public final class Junk {
  public void sillyMethod() {
    Runnable r = () -> {
      int i = 0;
      System.out.println(i);
    };
  }
}



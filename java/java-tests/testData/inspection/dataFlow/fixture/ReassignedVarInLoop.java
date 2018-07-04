import java.util.*;

public class ReassignedVarInLoop {
  public static void test(String text) {
    List<String> result = new ArrayList<String>();
    int start = -1;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      boolean flag = Character.isJavaIdentifierPart(c);
      if (flag && start == -1) {
        start = i;
      }
      if (flag && <warning descr="Condition 'start != -1' is always 'true' when reached">start != -1</warning>) {
        result.add("foo");
      }
      else if (<warning descr="Condition '!flag' is always 'true'">!flag</warning> && start != -1) {
        result.add("foo");
        start = -1;
      }
    }
  }
}

import java.util.Set;

public class UnknownComparedToNullable {
  native Object getObject(int x);

  void test(int val) {
    Object s1 = val < 0 ? null : getObject(val * 2);
    Object s2 = getObject(val);
    if (s1 != s2) {}
    System.out.println(s2.hashCode());
  }
}
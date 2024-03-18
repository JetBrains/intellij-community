import java.util.List;

class SeveralIfStatements {
  private static int test(Object obj) {
    obj.getClass();
    <warning descr="'if' statement can be replaced with 'switch' statement">if</warning> (obj instanceof List) {
      return ((List)obj).size();
    }
    else if (obj instanceof Character) {
      return 1;
    }
    if (obj instanceof String) {
      return ((String)obj).length();
    }
    if (obj instanceof Double) {
      return 2;
    }
    if (obj instanceof Integer) {
      return 3;
    }
    if (obj instanceof Float) {
      return 4;
    }
    throw new IllegalArgumentException();
  }
}
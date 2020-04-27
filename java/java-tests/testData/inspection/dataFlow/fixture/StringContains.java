import org.jetbrains.annotations.*;

class Test {
  public static boolean test(@Nullable String name, @NotNull String prefix) {
    return name != null && name.startsWith(prefix) && name.length() > prefix.length();
  }
  
  void test1(String s1, String s2) {
    if (<warning descr="Condition 's1.contains(s1)' is always 'true'">s1.contains(s1)</warning>) {}
    
    if (s1.contains(s2) || <warning descr="Condition 's1.equals(s2)' is always 'false' when reached">s1.equals(s2)</warning>) {}
  }
}
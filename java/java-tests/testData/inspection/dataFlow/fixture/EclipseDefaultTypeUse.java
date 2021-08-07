import org.eclipse.jdt.annotation.NonNullByDefault;

import java.util.ArrayList;

@NonNullByDefault
public class EclipseDefaultTypeUse {

  static void test(String x) {
    System.out.println("x  = " + x.toLowerCase());
  }

  public static void main(String[] args) {
    // Direct NonNullByDefault with PARAMETER target
    test(<warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>); 
    ArrayList<String> a=new ArrayList<>();
    // List::add is not annotated but ArrayList<String> is defined in context of TYPE_USE NonNullByDefault annotation
    a.add(<warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>); 
  }

  void local() {
    String s = null;
  }
}
@NonNullByDefault
@FunctionalInterface
interface DefNotNull {
  String apply(String s);
}
@FunctionalInterface
interface Plain {
  String apply(String s);
}
@NonNullByDefault
class UseNotNull {
  void test() {
    DefNotNull l1 = s -> <warning descr="Condition 's == null' is always 'false'">s == null</warning> ? "null" : s.isEmpty() ? <warning descr="'null' is returned by the method declared as @NonNullByDefault">null</warning> : s;
    Plain l2 = s -> s == null ? "null" : s.isEmpty() ? null : s;
  }
}
class UsePlain {
  void test() {
    DefNotNull l1 = s -> <warning descr="Condition 's == null' is always 'false'">s == null</warning> ? "null" : s.isEmpty() ? <warning descr="'null' is returned by the method declared as @NonNullByDefault">null</warning> : s;
    Plain l2 = s -> s == null ? "null" : s.isEmpty() ? null : s;
  }
}
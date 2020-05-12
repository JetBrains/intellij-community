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
}
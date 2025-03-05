import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.text.*;

class Test {
  final class Point {
    private int x;
    private int y;
    
    public int getX() {
      return x;
    }
    public void setX(int x) {
      this.x = x;
    }
    public int getY() {
      return y;
    }
    public void setY(int y) {
      this.y = y;
    }
  }
  
  final class NullityTest {
    private String s;
    private @Nullable String ns;
    private @NotNull String nns;
    
    public void setS(@NotNull String s) {
      this.s = s;
    }
    
    public void setS2(@Nullable String s) {
      this.s = <warning descr="Expression 's' might be null but is assigned to non-annotated field">s</warning>;
    }
    
    public void setNs(@NotNull String ns) {
      this.ns = ns;
    }
    
    public void setNs2(@Nullable String ns) {
      this.ns = ns;
    }
    
    public void setNns(@NotNull String nns) {
      this.nns = nns;
    }
    
    public void setNns2(@Nullable String nns) {
      this.nns = <warning descr="Expression 'nns' might evaluate to null but is assigned to a variable that is annotated with @NotNull">nns</warning>;
    }
  }
  
  void use(Point point) {
    point.setX(1);
    point.setY(2);
    if (<warning descr="Condition 'point.getX() == 1 && point.getY() == 2' is always 'true'"><warning descr="Condition 'point.getX() == 1' is always 'true'">point.getX() == 1</warning> && <warning descr="Condition 'point.getY() == 2' is always 'true' when reached">point.getY() == 2</warning></warning>) {
      System.out.println("ok");
    }
  }
  
  void useNullity(NullityTest nullityTest) {
    nullityTest.setS(<warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>);
    nullityTest.setS2(null);
    nullityTest.setNs(<warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>);
    nullityTest.setNs2(null);
    nullityTest.setNns(<warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>);
    nullityTest.setNns2(null);
  }
}
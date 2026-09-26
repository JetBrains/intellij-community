import java.util.*;
import org.jetbrains.annotations.*;

class Test {
  record MyRecord(int value) {}
  
  void testRecord(MyRecord record) {
    if (record.value() == 0) {
      if (<warning descr="Condition 'record.value == 0' is always 'true'">record.value == 0</warning>) {}
    }
  }
  
  void testPoint(Point p1, Point p2) {
    if (p1.x == p2.x) {
      if (<warning descr="Condition 'p1.getX() != p2.getX()' is always 'false'">p1.getX() != p2.getX()</warning>) {
        
      }
      if (p1.getXBoxed() == null) {}
      if (p1.getXBoxed() != p2.getXBoxed()) {
        
      }
    }
    p2.y = 5;
    if (<warning descr="Condition 'p2.getY() > 0' is always 'true'">p2.getY() > 0</warning>) {}
  }
  
  void testWithNullity(WithNullity w) {
    if (<warning descr="Condition 'w.getS() == null' is always 'false'">w.getS() == null</warning>) {
    }
    // Not inlined, because method is declared as nullable, while field is not
    System.out.println(w.getS2().<warning descr="Method invocation 'trim' may produce 'NullPointerException'">trim</warning>());
    // Inlined, nullability is taken from the field (optional warning inside the method)
    System.out.println(w.getS3().<warning descr="Method invocation 'trim' may produce 'NullPointerException'">trim</warning>());
    // Inlined, not-null nullability is forced by method declaration (but we have a warning inside the method)
    System.out.println(w.getS4().trim());
  }
  
  static final class WithNullity {
    String s;
    String s2;
    @Nullable String s3;
    @Nullable String s4;
    
    @NotNull
    String getS() {
      return s;
    }
    
    @Nullable
    String getS2() {
      return s2;
    }
    
    String getS3() {
      return <warning descr="Expression 's3' might evaluate to null but is returned by the method which is not declared as @Nullable">s3</warning>;
    }

    @NotNull
    String getS4() {
      return <warning descr="Expression 's4' might evaluate to null but is returned by the method declared as @NotNull">s4</warning>;
    }
  }
  
  static final class Point {
    int x, y;
    
    Point(int x, int y) {
      this.x = x;
      this.y = y;
    }
    
    int getX() {
      return x;
    }
    
    int getY() {
      return y;
    }
    
    Integer getXBoxed() { // Boxing should not be supported intentionally
      return x;
    }
  }
}
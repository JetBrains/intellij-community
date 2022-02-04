// "Add constructor parameter" "true"
import java.lang.annotation.*;
public class A {
  @I
  private int field;
  private @Existing String str;

  public A(@I int field, @Existing String str) {
      this.field = field;
      this.str = str;
  }
}

@Target({ElementType.TYPE_USE})
@interface I {}
@Target({ElementType.TYPE_USE, ElementType.PARAMETER})
@interface Existing {}
// "Add constructor parameter" "true"
import java.lang.annotation.*;
public class A {
  @I
  private int <caret>field;
  private @Existing String str;

  public A(@Existing String str) {
    this.str = str;
  }
}

@Target({ElementType.TYPE_USE})
@interface I {}
@Target({ElementType.TYPE_USE, ElementType.PARAMETER})
@interface Existing {}
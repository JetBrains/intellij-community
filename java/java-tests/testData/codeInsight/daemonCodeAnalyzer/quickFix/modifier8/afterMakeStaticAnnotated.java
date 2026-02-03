// "Make 'foo()' static" "true-preview"
import java.lang.annotation.*;

interface I {
  public static @Anno int foo() {
    System.out.println();
  }
}
@Target(ElementType.TYPE_USE)
@interface Anno {}
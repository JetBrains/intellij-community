// "Make 'foo()' static" "true-preview"
import java.lang.annotation.*;

interface I {
  public @Anno int f<caret>oo() {
    System.out.println();
  }
}
@Target(ElementType.TYPE_USE)
@interface Anno {}
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE, ElementType.TYPE_USE})
@interface Anno {}

class MyClass {
  {
    <error descr="'var' type may not be annotated">@Anno</error> var s = "hello";
  }
}
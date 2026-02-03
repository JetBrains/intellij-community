
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.PARAMETER, ElementType.TYPE_USE})
@interface Anno {
  String value() default "";
}

class Test {
  void foo(@Anno String s) {
      newMethod(s);
  }

    private void newMethod(@Anno String s) {
        if (s == null) {}
    }
}

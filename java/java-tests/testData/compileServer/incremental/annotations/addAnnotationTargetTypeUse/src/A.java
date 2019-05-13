import java.lang.annotation.*;
import static java.lang.annotation.ElementType.*;

@Target({TYPE, FIELD, TYPE_USE})
public @interface A {
  int value() default 3;
}
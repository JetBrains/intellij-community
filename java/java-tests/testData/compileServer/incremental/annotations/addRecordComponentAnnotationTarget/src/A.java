import java.lang.annotation.*;
import static java.lang.annotation.ElementType.*;

@Target({FIELD})
public @interface A {
  int value() default 3;
}
// "Cast to 'int'" "true"
import java.lang.annotation.*;
import static java.lang.annotation.ElementType.*;

@Target({TYPE_USE}) @interface TA { String value() default ""; }

class C {
  {
    Object o = null;
    @TA("wtf") <caret>int i = o;
  }
}

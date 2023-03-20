// "Cast expression to 'int[]'" "true-preview"
import java.lang.annotation.*;
import static java.lang.annotation.ElementType.*;

@Target({TYPE_USE}) @interface TA { }

class C {
  {
    Object o = null;
    @TA <caret>int @TA [] a = o;
  }
}

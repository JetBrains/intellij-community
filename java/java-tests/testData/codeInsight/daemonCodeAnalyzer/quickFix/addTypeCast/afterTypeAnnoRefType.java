// "Cast to 'java.lang.Integer'" "true"
import java.lang.annotation.*;
import static java.lang.annotation.ElementType.*;

@Target({TYPE_USE}) @interface TA { }

class C {
  {
    Object o = null;
    @TA <caret>Integer i = (@TA Integer) o;
  }
}

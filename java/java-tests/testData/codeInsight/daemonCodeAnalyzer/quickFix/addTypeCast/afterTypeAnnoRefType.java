// "Cast to 'java.util.List<java.lang.Integer>'" "true"
import java.lang.annotation.*;
import java.util.*;
import static java.lang.annotation.ElementType.*;

@Target({TYPE_USE}) @interface TA { }

class C {
  {
    Object o = null;
    @TA List<@TA Integer> i = (List<Integer>) o;
  }
}

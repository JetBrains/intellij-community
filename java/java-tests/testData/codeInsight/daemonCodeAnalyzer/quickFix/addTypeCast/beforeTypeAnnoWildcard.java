// "Cast to 'java.util.List<? extends java.lang.String>'" "true"
import java.lang.annotation.*;
import java.util.List;
import static java.lang.annotation.ElementType.*;

@Target({TYPE_USE}) @interface TA { }

class C {
  {
    Object o = null;
    @TA <caret>List<@TA ? extends @TA String> l = o;
  }
}

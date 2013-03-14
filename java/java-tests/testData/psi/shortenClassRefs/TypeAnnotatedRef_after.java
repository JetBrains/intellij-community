import java.lang.annotation.*;
import static java.lang.annotation.ElementType.*;

@Target({TYPE_USE}) @interface TA { }

class Outer {
  class Middle {
    class Inner {
      void m1(Inner p) { }
      void m2(@TA Outer.Middle.Inner p) { }
      void m3(@TA Middle.Inner p) { }
      void m4(@TA @TA Inner p) { }
    }
  }
}

import java.lang.annotation.*;
import static java.lang.annotation.ElementType.*;

@Target({TYPE_USE}) @interface TA { }

class Outer {
  class Middle {
    class Inner {
      void m1(Outer.Middle.Inner p) { }
      void m2(@TA Outer.Middle.Inner p) { }
      void m3(Outer.@TA Middle.Inner p) { }
      void m4(Outer.Middle.@TA @TA Inner p) { }
    }
  }
}

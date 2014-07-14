import java.lang.annotation.*;
import java.util.List;

@Target({ElementType.TYPE_USE})
@interface TA { int value() default 0; }

class C {
  class Inner { }

  //public @TA(0) List<@TA(1) C.@TA(1) Inner> m(@TA(2) int @TA(3) [] p1, @TA(4) List<@TA(5) Class<@TA(6) ?>> p2, @TA(7) String @TA(8) ... p3) throws @TA(42) IllegalArgumentException, @TA(43) IllegalStateException {
  public List<Inner> m<caret>() {
    return null;
  }
}

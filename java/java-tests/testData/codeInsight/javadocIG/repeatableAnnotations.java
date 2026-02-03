import java.lang.annotation.*;
import java.util.ArrayList;
import java.util.List;

@R("a")
@R("b")
class repeatableAnnotations {}

@Documented
@Repeatable(R.Rs.class)
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.FIELD, ElementType.TYPE})
@interface R {
  String value();
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target({ElementType.TYPE, ElementType.FIELD})
  public @interface Rs {
    R[] value();
  }
}
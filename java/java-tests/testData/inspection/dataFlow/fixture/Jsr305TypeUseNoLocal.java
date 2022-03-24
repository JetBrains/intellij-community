import javax.annotation.*;
import javax.annotation.meta.*;
import java.lang.annotation.*;

@NonNullByDefault
public class Jsr305TypeUseNoLocal {
  void test() {
    Object object = query();
    Object objectNotNull = queryNotNull();
    if (object == null) {}
    if (<warning descr="Condition 'objectNotNull == null' is always 'false'">objectNotNull == null</warning>) {}
  }

  native Object queryNotNull();
  @Nullable native Object query();
}
@Nonnull
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@TypeQualifierDefault({ ElementType.METHOD, ElementType.PARAMETER, ElementType.FIELD, ElementType.TYPE_USE })
@interface NonNullByDefault {
}
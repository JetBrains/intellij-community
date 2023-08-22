import javax.annotation.*;
import javax.annotation.meta.*;
import java.lang.annotation.*;
import org.jetbrains.annotations.NotNull;

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

@NotNullByDefault
class Mixed {
  void test(String s) {
    if (<warning descr="Condition 's == null' is always 'false'">s == null</warning>) {}
  }
}

// Error due to mock limitation
<error descr="'@NotNull' not applicable to annotation type">@NotNull</error>
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@TypeQualifierDefault({ ElementType.METHOD, ElementType.PARAMETER, ElementType.FIELD, ElementType.TYPE_USE })
@interface NotNullByDefault {
}


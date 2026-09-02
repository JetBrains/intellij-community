import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.meta.TypeQualifierDefault;
import java.lang.annotation.*;

@NonNullImpl
class BugReproduction {
  public static void main(String[] args) {
    doSomething(null);
  }

  public static void doSomething(@Nullable String string) {
    System.out.printf("%b%n", string);
  }
}

@Documented
@Inherited
@Nonnull
@Retention(RetentionPolicy.RUNTIME)
@Target({
  ElementType.PACKAGE,
  ElementType.TYPE,
})
@TypeQualifierDefault({
  ElementType.FIELD,
  ElementType.TYPE_USE,
})
@interface NonNullImpl {
}

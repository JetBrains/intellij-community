import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.meta.TypeQualifierNickname;
import javax.annotation.meta.When;

@ParametersAreNonnullByDefault
final class Test<T> {
  @Nonnull(when = When.UNKNOWN)
  @TypeQualifierNickname
  @interface UnknownNullity {}

  @UnknownNullity
  private final T value;

  @Nonnull(when = When.UNKNOWN)
  private final T value2;

  public Test(@UnknownNullity T value, @Nonnull(when = When.UNKNOWN) T value2) {
    this.value = value;
    this.value2 = value2;
  }

  public boolean isNull() {
    return value == null || value2 == null;
  }
}
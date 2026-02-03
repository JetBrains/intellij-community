import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.framework.qual.DefaultQualifier;

@DefaultQualifier(NonNull.class)
class Temp {

  void run() {
    Object obj = Objects.requireNonNull(maybeNull());
  }

  @Nullable Object maybeNull() {
    return ThreadLocalRandom.current().nextBoolean() ? null : new Object();
  }

}
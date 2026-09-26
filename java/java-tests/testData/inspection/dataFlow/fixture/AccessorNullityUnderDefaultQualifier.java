import org.checkerframework.checker.nullness.qual.*;
import org.checkerframework.framework.qual.*;
import org.jetbrains.annotations.NotNull;

@DefaultQualifier(NonNull.class)
record SimpleRecord(String @Nullable [] nullableArray) {
  public static void main() {
    SimpleRecord record = createRecord();
    if (record.nullableArray() == null) {
      // etc...
    }
  }

  private static @NotNull SimpleRecord createRecord() {
    return new SimpleRecord(null);
  }
}
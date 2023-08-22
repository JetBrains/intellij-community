import org.checkerframework.checker.nullness.qual.*;
import org.checkerframework.framework.qual.*;

@DefaultQualifier(NonNull.class)
record SimpleRecord(String @Nullable [] nullableArray) {
  public static void main() {
    SimpleRecord record = new SimpleRecord(null);
    if (record.nullableArray() == null) {
      // etc...
    }
  }
}
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.stream.Collectors;

@NullMarked
class Main {
  public static void main(String[] args) {
    AtomicReference<@Nullable String> ref = new AtomicReference<caret><>();
    AtomicReference<String> ref1 = new AtomicReference<@Nullable String>();
    AtomicReference<String> ref2 = new AtomicReference<>(null);
    AtomicReference<String> ref3 = new AtomicReference<>("");

    ThreadLocal<@Nullable String> tl = new ThreadLocal<>();
    ThreadLocal<String> tl2 = new ThreadLocal<>() {
      @Override
      protected String initialValue() {
        return null;
      }
    };
    ThreadLocal<String> tl3 = ThreadLocal.withInitial(() -> null);

    AtomicReferenceArray<@Nullable String> arr = new AtomicReferenceArray<>(10);
    AtomicReferenceArray<String> arr2 = new AtomicReferenceArray<>(new String[10]); // technically wrong and should be highlighted, as array contains nulls
    @Nullable String[] data = new String[10];
    AtomicReferenceArray<String> arr3 = new AtomicReferenceArray<>(data); // technically wrong and should be highlighted, as array contains nulls
  }
}
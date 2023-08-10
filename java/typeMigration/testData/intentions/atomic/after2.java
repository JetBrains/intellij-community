import java.util.concurrent.atomic.AtomicReferenceArray;

// "Convert to atomic" "true-preview"
class Test {
    final AtomicReferenceArray<Object> field = new AtomicReferenceArray<>(foo());
  Object[] foo() {
    return null;
  }
}
import java.util.concurrent.atomic.AtomicReferenceArray;

// "Convert to atomic" "true"
class Test {
  final AtomicReferenceArray<Object> field= new AtomicReferenceArray<>(foo());
  Object[] foo() {
    return null;
  }
}
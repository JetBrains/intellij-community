import java.util.concurrent.atomic.AtomicReferenceArray;

// "Convert to atomic" "true"
class Test {
  final AtomicReferenceArray<String> field= new AtomicReferenceArray<>(new String[]{});
}
// "Convert to atomic" "true-preview"

import java.util.concurrent.atomic.AtomicReferenceArray;

class X {

    final AtomicReferenceArray<String[]> field = new AtomicReferenceArray<>(foo());

  String[][] foo() {
    return null;
  }
}
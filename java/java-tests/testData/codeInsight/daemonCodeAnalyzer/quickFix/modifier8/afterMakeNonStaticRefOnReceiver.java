// "Make 'PrivateMethodRef.filter()' not static" "true-preview"

import java.util.function.Predicate;

class PrivateMethodRef {
  boolean filter() {
    return false;
  }
}

class Some {
  public static void main(String[] args) {
    PrivateMethodRef f = null;
    Predicate<PrivateMethodRef> filter2 = PrivateMethodRef::filter;
  }
}
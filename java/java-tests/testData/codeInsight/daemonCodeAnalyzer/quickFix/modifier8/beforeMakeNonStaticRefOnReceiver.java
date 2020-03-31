// "Make 'PrivateMethodRef.filter' not static" "true"

import java.util.function.Predicate;

class PrivateMethodRef {
  static boolean filter() {
    return false;
  }
}

class Some {
  public static void main(String[] args) {
    PrivateMethodRef f = null;
    Predicate<PrivateMethodRef> filter2 = PrivateMethodRef::fil<caret>ter;
  }
}
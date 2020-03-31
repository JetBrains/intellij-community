// "Make 'PrivateMethodRef.filter' static" "true"

import java.util.function.Predicate;

class PrivateMethodRef {
  boolean filter(final String s) {
    return false;
  }
}

class Some {
  public static void main(String[] args) {
    Predicate<String> filter2 = PrivateMethodRef::fi<caret>lter;
  }
}
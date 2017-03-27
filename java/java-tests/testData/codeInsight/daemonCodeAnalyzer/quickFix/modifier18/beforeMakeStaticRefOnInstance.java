// "Make 'filter' static" "false"

import java.util.function.Predicate;

class PrivateMethodRef {
  boolean filter(final String s) {
    return false;
  }
}

class Some {
  public static void main(String[] args) {
    PrivateMethodRef ref = new PrivateMethodRef();
    Predicate<String> filter2 = ref::fi<caret>lter;
  }
}
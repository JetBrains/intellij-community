// "Make 'PrivateMethodRef.filter' package-private" "true"
import java.util.function.Predicate;

class PrivateMethodRef {
  private static boolean filter(final String s) {
    return false;
  }
}

class Some {
  public static void main(String[] args) {
    Predicate<String> filter2 = PrivateMethodRef::fi<caret>lter;
  }
}
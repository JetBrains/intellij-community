import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
class NullAnnotation {
  byte @Nullable [] getArray() {
    return null;
  }

  void handleArray() {
    byte[] array = getArray();

    if (array == null) {
      return;
    }
    for (byte b : array) {
      System.out.println(b);
    }
  }
}
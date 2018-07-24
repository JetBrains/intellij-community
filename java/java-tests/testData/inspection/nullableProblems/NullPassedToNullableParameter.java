import org.jetbrains.annotations.Nullable;

class AcceptsNull {
  void recepient(@Nullable String s) {
    System.out.println("s=" + s);
  }

  void donor() {
    recepient(null);
  }
}
import org.jetbrains.annotations.Nullable;

class Some {
  public static void main(String arg, @Nullable StringBuilder sb) {
    if (arg != null) {
      return;
    }

    if (sb != null) { }

    if (sb != null) { }
  }

}

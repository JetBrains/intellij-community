import org.jetbrains.annotations.Nullable;

class Some {
  void bar2(@Nullable Iterable<? extends String> dirs) {
    if (dirs != null) {
      for (String dir : dirs) {

      }
    }
  }

}
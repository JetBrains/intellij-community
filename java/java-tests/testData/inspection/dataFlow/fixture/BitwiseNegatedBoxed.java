import org.jetbrains.annotations.Nullable;

class NullTest {

  public void clear(@Nullable Integer mask) {
    if (mask == null) {
      return;
    }

    int i = ~mask;
    int i2 = ~mask;
  }

}
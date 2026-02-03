import org.jetbrains.annotations.Nullable;

class City {
  @Nullable
  private String name;

  @Override
  @Nullable
  public String toString() {
    return name;
  }
}

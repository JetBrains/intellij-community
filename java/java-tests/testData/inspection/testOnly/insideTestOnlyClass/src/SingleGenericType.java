import org.jetbrains.annotations.TestOnly;

@TestOnly
public class SingleGenericType implements Comparable<SingleGenericType> {
  private final boolean isLeftBorder;
  private final int offset;
  private final String text;  

  public SingleGenericType(boolean isLeftBorder, int offset, String text) {
    this.isLeftBorder = isLeftBorder;
    this.offset = offset;
    this.text = text;
  }

  @Override
  public int compareTo(@NotNull SingleGenericType o) {
    return offset < o.offset ? 1 : -1;
  }
}
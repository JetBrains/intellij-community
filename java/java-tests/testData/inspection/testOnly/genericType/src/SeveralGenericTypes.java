import org.jetbrains.annotations.TestOnly;

@TestOnly
public class SeveralGenericTypes implements BiFunction<SeveralGenericTypes, Integer, SeveralGenericTypes> {
  private final boolean isLeftBorder;
  private final int offset;
  private final String text;

  public SeveralGenericTypes(boolean isLeftBorder, int offset, String text) {
    this.isLeftBorder = isLeftBorder;
    this.offset = offset;
    this.text = text;
  }

  @Override
  public SeveralGenericTypes apply(SeveralGenericTypes first, Integer second) {
    return first;
  }
}
// "Use lombok @Getter for 'finalField'" "true"

public class Foo {
  private int fieldWithoutGetter;
  /**
   * The final field.
   */
  private final int finalField;

  /**
   * Returns The final field.
   *
   * @return The final field
   */
  public int getFinalField() {
    return finalField<caret>;
  }
}
public class Foo {
  public void handleInsert(InsertionContext context, LookupElement item) {
    if (hasParams(<caret>))
  }

  private static boolean hasParams(InsertionContext context, LookupElement item) {
    return false;
  }
}

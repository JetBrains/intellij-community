public class Foo {
  public void handleInsert(InsertionContext context, LookupElement item) {
    if (hasParams(context, item)<caret>)
  }

  private static boolean hasParams(InsertionContext context, LookupElement item) {
    return false;
  }
}

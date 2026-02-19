// "Remove pattern variable" "true-preview"
class X {
  public void test(Object object) {
    if (object instanceof String str<caret>ing) {}
  }
}
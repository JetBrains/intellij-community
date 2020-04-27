// "Remove pattern variable" "true"
class X {
  public void test(Object object) {
    if (object instanceof String str<caret>ing) {}
  }
}
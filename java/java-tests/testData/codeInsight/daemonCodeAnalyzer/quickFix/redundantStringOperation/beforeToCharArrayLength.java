// "Remove unnecessary 'toCharArray()' call" "true-preview"
class Foo {
  public void x(String s) {
    return s.<caret>toCharArray().length;
  }
}
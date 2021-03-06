// "Split into declaration and assignment" "true"

class Foo {
  {
    <caret>int fieldIndexes = getFieldIndexes(Hellonew String[]{""});
  }
  public int getFieldIndexes(String[] columns) {
    return 0;
  }
}
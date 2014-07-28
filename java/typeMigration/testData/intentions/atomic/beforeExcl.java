// "Convert to atomic" "true"
class Test {
  boolean <caret>field=false;
  {
    boolean b = !field;
  }
}
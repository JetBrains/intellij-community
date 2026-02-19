// "Use 'contentEquals()' and remove redundant 'toString()' call" "true-preview"
class Main {
  boolean foo(String s, StringBuffer sb) {
    return ((s)).equals(((((sb)).toString<caret>())));
  }
}
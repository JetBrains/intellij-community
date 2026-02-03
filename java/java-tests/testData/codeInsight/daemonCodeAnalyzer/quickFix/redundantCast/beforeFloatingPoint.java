// "Remove redundant cast" "true-preview"
class Test {
  {
    double d = -1e20 + (do<caret>uble)(1e20 - 1);
  }
}
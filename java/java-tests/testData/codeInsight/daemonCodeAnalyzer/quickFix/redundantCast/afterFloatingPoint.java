// "Remove redundant cast(s)" "true-preview"
class Test {
  {
    double d = -1e20 + (1e20 - 1);
  }
}
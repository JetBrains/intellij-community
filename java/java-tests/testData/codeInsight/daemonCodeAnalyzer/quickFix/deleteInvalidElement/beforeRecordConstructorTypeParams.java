// "Remove type parameters" "true-preview"
record R() {
  <T, <caret>U> R() {}
}
// "Replace with expression lambda" "true-preview"
class Test {
  {
    Comparable<String> c = (o) -> {r<caret>eturn 0;};
  }
}
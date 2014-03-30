// "Replace with expression lambda" "true"
class Test {
  {
    Comparable<String> c = (o) -> {r<caret>eturn 0;};
  }
}
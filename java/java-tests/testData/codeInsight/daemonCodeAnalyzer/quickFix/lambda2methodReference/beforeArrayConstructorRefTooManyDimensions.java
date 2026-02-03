// "Replace lambda with method reference" "false"
class Test {
  {
    java.util.stream.IntStream.range(0, 8).mapToObj(i -> new St<caret>ring[i][i]);
  }
}
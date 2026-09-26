// "Unimplement" "true-preview"
interface Iface {
  int value();
}
record R(int value) implements Ifa<caret>ce {}
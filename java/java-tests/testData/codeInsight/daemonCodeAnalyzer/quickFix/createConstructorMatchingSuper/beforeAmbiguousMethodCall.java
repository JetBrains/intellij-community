// "Create constructor matching super" "true"
class X {
  X(int... a) {}
  X(String... b) {}
}

class Y<caret> extends X {
}
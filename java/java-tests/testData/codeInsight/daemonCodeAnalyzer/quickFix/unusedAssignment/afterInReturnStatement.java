// "Remove redundant assignment" "true-preview"
class A {
  int v() {
        int x;
        return getValue();
    }

    private int getValue() {
        return 0;
    }
}
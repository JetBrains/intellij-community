// "Remove redundant assignment" "true-preview"
class A {
  int v() {
        int x;
        return <caret>x = getValue();
    }

    private int getValue() {
        return 0;
    }
}
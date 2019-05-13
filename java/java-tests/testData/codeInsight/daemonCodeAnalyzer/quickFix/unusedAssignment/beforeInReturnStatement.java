// "Remove redundant assignment" "true"
class A {
  int v() {
        int x;
        return <caret>x = getValue();
    }

    private int getValue() {
        return 0;
    }
}
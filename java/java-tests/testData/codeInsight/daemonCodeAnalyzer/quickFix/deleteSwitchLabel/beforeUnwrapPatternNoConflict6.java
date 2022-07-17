// "Remove unreachable branches" "false"
class Test {
    Test() {
      this(1 + switch ((Integer) 1) {
        case <caret>Integer i && i == 1 -> i;
        case Integer i -> i;
      });
    }

    Test(int n) {
    }
}
// "Replace 'switch' with 'if'" "true"
class X {
  void test(int i) {
    <caret>switch (i) {
        case 1:
            //foo
            if (Math.random() > 0.5) {
                System.out.println("Hello");
            }
        }
    }

}
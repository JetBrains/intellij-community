// "Remove qualifier" "true-preview"
class Test {
    void test(Letter obj) {
        switch (obj) {
            case Letter.LETTER_A: case LETTER_B:
              System.out.println(10);
              break;
        }
    }

  enum Letter {LETTER_A, LETTER_B, LETTER_C}
}
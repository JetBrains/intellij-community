// "Add on-demand static import for 'test.Test.Letter'" "true-preview"
package test;

class Test {
  void test(Letter obj) {
    switch (obj) {
      case <caret>Letter.LETTER_A, Letter.LETTER_B:
        System.out.println(10);
        break;
      case Letter l:
        System.out.println(20);
        break;
    }
  }

  enum Letter {LETTER_A, LETTER_B, LETTER_C}
}

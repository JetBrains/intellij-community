// "Create missing branch 'A'" "false"
enum Z {
  B,
  A,
  C
}

class X {
  public void test(Z z) {
    switch (z) {

      case C:
      case B:
        switch (z) {
          case B:
            <caret>break;

        }
        break;
    }
  }
}

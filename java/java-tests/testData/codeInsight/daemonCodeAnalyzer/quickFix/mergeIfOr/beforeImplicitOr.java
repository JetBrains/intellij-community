// "Merge with the next 'if' using ||" "true"

class ImplicitOr {

  void liability() {
    <caret>if /*equivocal*/(true) {
      System.out.println();
      // atavistic
      return;
    }
    if/*indubious*/ (true) {
      System.out.println();
      // vestigial
      return;
    }
  }
}
// "Merge sequential 'if' statements" "true"

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
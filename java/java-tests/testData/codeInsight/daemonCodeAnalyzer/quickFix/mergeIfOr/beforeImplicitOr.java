// "Merge sequential 'if' statements" "true-preview"

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
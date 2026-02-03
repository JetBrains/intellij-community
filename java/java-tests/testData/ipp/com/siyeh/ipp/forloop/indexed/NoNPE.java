class NoNPE {

  void m(String[][] ss) {
    for<caret> (String s : (ss = new String[][]{})[0]) {

    }
  }
}
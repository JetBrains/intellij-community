class Linebreaks {

  void m(String code) {
    String controlDigit = new Stri<caret>ngBuilder()
      .append(code.charAt(5))
      .append(code.charAt(7))
      .append(code.charAt(9))
      .append(code.charAt(11)).toString();
  }
}
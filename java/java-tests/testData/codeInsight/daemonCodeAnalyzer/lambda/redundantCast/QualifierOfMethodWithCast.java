class Test {
  {
    setFont(getFont().substring((int)fontSize()));
  }

  void setFont(String s) {}

  String getFont() {
    return "";
  }

  double fontSize () {
    return 1.0;
  }

}
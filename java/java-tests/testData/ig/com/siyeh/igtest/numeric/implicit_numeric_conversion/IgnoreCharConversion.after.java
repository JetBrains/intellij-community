class IgnoreCharConversion {

  void m(int i) {
    char a = 'a';
    i = a;
    i += a;
    a += 1;
    byte b = 1;
      a = (char) (a + (int) b);
  }

  void ridiculous() {
    char x = 2;
    x *= 3.1;
  }
}
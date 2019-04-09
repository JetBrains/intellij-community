class C {
  String test(int n) {
    String s = "";
    <selection>
    if (n == 1) {
      return "A" + s;
    }
    if (n == 2) {
      return "B" + s;
    }
    </selection>
    return null;
  }
}
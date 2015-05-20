class Test {
  String foo(boolean b) {
    <selection>
    if (b) {
      return "a";
    }
    if (!b) {
      return "b";
    } 
    </selection>
    return "42";
  }
}
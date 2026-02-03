class MultiCatch {

  public String get(boolean b) {
    try {
      return b ? get1() : get2();
    } catch (EE1 | EE2 e) {
      return null; // is reachable
    } catch (E1 | E2 e) {
      //
    }
    return null;
  }

  String get1() throws E1 { return "1"; }
  String get2() throws E2 { return "2"; }

  static class E extends Exception { }
  static class E1 extends E { }
  static class E2 extends E { }
  static class EE1 extends E1 { }
  static class EE2 extends E2 { }
}
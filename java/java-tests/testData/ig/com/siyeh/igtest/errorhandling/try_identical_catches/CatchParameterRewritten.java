class C {
  void foo() {
    try {
      bar();
    } catch (NullPointerException e) {
      e.printStackTrace();
      e = null;
    } catch (IllegalStateException e) {
      e.printStackTrace();
      e = null;
    } catch (ClassCastException e) {
      e.printStackTrace();
      e = null;
    }
  }

  void bar(){}
}
class K {
  void m() {
    class Local {
      void locally() {}
    }
    <selection>new Local()</selection>.locally();
  }
}
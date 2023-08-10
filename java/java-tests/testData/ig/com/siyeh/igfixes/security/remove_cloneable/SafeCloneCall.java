class <caret>SafeCloneCall implements Cloneable {

  void x() {
    new Object() {{
      try {
        Object clone = clone();
      } catch (CloneNotSupportedException e) {
        e.printStackTrace();
      }
    }};
  }
}
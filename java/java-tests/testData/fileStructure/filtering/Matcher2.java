interface Matcher2 {
  void isControlDown();
  void getIcon();

  int MIX_SCORE = 5;

  class A {
    Icon getIcon(){return null;}
    boolean isCoin(){return false;}
  }

  class B <caret>{
    Icon getIcon(){return null;}
    boolean isCoin(){return false;}
  }

  Object o = new Object() {
    Icon getIcon() {
      return null;
    }
  };
}

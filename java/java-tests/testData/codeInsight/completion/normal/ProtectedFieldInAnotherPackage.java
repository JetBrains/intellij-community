class Zoo extends foo.Super {
  void foo() {
    new Runnable() {
      @Override
      public void run() {
        myStr<caret>
      }
    };
  }

}

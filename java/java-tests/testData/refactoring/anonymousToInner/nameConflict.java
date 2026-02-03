class ConvertAnonymousToInner {

  boolean myFast = true;

  void convert(boolean fast) {
    new Object() {<caret>
      public String toString() {
        if (myFast != fast) {
          myFast = fast;
          System.out.println(fast);
        }
        return "";
      }
    };
  }
}
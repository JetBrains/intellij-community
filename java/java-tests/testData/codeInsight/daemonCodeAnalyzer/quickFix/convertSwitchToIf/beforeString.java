// "Replace 'switch' with 'if'" "true"
class X {
  public void doSomething( String value) {
    switch<caret> ( value ) {
      case "case1"://comment1
        //comment2
        break;//comment3
      case "case2"://comment4
        break;//comment5
      default://comment6
        break;//comment7
    }//comment8
  }
}
// "Replace 'switch' with 'if'" "true"
class X {
  public void doSomething( String value) {
    switch<caret> ( value ) {
      case "case1":
        break;
      case "case2":
        break;
      default:
        break;
    }
  }
}
// "Replace 'switch' with 'if'" "true"
class X {
  void test4() throws IOException {
    String variable = "abc";
    <caret>switch (variable) {
      case "abc": {
        String s1 = "abcd";
        break;
      }
      case "def": {
        String s1 = "abcd";
        myFunction(s1);
        break;
      }
      default:
        throw new IllegalArgumentException();
    }
  }

  public void myFunction(Object o1) throws IOException {
  }
}
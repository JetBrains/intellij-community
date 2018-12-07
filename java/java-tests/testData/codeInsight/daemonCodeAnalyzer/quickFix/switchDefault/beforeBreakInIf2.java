// "Unwrap 'switch'" "true"
class X {
  String test(char c) {
    s<caret>witch (c) {
      default:
        if(c == 'a') {
          System.out.println("foo");
          break;
        }
        break;
    }
    System.out.println("oops");
  }
}
// "Unwrap 'switch'" "true"
class X {
  String test(char c) {
    s<caret>witch (c/*comment*/) {
      default:
        if(c == 'a') {
          System.out.println("foo");
        }
        break;
    }
  }
}
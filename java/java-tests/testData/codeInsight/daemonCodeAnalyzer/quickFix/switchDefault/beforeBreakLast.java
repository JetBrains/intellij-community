// "Unwrap 'switch'" "true-preview"
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
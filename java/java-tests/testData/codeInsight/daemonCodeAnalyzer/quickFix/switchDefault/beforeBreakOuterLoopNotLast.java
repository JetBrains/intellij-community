// "Unwrap 'switch'" "false"
class X {
  String test(char c) {
    for(int i=0; i<10; i++) {
      s<caret>witch (c){
      default:
        if (c == 'a') {
          System.out.println("foo");
          break;
        }
        System.out.println("bar");
      }
      System.out.println("oops");
    }
    return "";
  }
}
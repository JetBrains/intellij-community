// "Unwrap 'switch'" "true"
class X {
  String test(char c) {
    for(int i=0; i<10; i++) {
        if (c == 'a') {
            System.out.println("foo");
            continue;
        }
        System.out.println("bar");
    }
    System.out.println("oops");
    return "";
  }
}
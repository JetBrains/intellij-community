class MyTest {

  boolean x(String s) {
      /*c1*/
      return !s<caret>.isEmpty() && s.charAt(0) == 'x'//c2 
              &&
              !s.endsWith("x");
  }
}
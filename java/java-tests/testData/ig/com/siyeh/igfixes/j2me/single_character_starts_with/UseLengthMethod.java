class UseLengthMethod {

  boolean x(String s) {
    return s.<warning descr="Single character 'startsWith()' can be replaced with 'charAt()' expression"><caret>startsWith</warning>("x");
  }
}
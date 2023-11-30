class Test {
  String foo() {
    StringBuffer <warning descr="'StringBuffer sb' may be declared as 'StringBuilder'"><caret>sb</warning> = new StringBuffer().append("a").append("|").append("b");
    return sb.toString();
  }
}
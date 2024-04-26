/*
Value is always false (str == null; line#9)
  'str' was passed as an argument to a method accepting non-null parameter (str; line#8)
    Parameter 'str' was inferred to be 'non-null' (str; line#13)
 */
class A {
  public A(String str) {
    foo(str);
    if (<selection>str == null</selection>){
    }
  }
  
  static void foo(String str) {
    System.out.println(str.trim());
  }
}
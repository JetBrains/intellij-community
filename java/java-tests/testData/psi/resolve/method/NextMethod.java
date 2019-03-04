class A{
 void foo(Object a, String b){
 }

 void foo(){
  <caret>foo("aaa", true);
 }

 void foo(String a, boolean b){
 }
}
// "Change parameter 'i' type to 'String'" "true"

class Ex{
 void foo(int i) {
   bar(<caret>i);
 }
 void bar(String s) {}
}
// "Change parameter 'i' type to 'String'" "true-preview"

class Base {
 void foo(int i) {}
}

class Ex extends Base {
 @Override
 void foo(String i) {
   i = "abc"; // when I invoke "Change type of 'i' to 'String'", base method is ignored so method overriding is lost
 }
}
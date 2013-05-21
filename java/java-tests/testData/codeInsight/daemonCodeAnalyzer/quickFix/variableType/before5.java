// "Change parameter 'i' type to 'java.lang.String'" "true"

class Base {
 void foo(int i) {}
}

class Ex extends Base {
 @Override
 void foo(int i) {
   i = <caret>"abc"; // when I invoke "Change type of 'i' to 'String'", base method is ignored so method overriding is lost
 }
}
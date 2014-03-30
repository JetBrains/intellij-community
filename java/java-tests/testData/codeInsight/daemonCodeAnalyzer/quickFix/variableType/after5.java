// "Change parameter 'i' type to 'java.lang.String'" "true"

class Base {
 void foo(String i) {}
}

class Ex extends Base {
 @Override
 void foo(String i) {
   i = "abc"; // when I invoke "Change type of 'i' to 'String'", base method is ignored so method overriding is lost
 }
}
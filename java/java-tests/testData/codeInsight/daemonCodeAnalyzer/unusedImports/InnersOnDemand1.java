import <error descr="Class 'OuterClass' is in the default package">OuterClass</error>.*;

class OuterClass {
  class Inner { }
}

class X {
  Inner inner;
}
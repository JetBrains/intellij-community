@interface A {
  int value();
}

@A(value = 0, <error descr="Duplicate attribute 'value'">value = 1</error>) class C {}
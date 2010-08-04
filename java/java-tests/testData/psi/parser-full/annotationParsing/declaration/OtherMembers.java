@interface Annotation {
  Class foo () default String.class;
  int field;
  void m() {}
  class C {}
  interface I {}
}
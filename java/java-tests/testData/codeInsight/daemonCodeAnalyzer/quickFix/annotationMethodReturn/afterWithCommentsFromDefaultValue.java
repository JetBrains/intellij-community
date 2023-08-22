// "Make 'value()' return 'int'" "true-preview"
class X {
  @interface MyAnnotation {
    /*1*/int/*2*/ /*3*/value(/*4*/)/*5*/ default /*6*/42<caret>/*7*/;
  }
}
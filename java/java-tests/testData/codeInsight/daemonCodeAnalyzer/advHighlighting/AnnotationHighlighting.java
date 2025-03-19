class <symbolName descr="null" type="CLASS_NAME">MyClass</symbolName> {

  <symbolName descr="null" type="ANNOTATION_NAME">@MyAnnotation</symbolName>
  <symbolName descr="null" type="ANNOTATION_NAME">@<error descr="Cannot resolve symbol 'Unresolved'">Unresolved</error></symbolName>
  void <symbolName descr="null" type="METHOD_DECLARATION">normal</symbolName>(){}

  <symbolName descr="null" type="ANNOTATION_NAME">@<symbolName descr="null" type="CLASS_NAME">MyClass</symbolName>.MyAnnotation</symbolName>
  void <symbolName descr="null" type="METHOD_DECLARATION">qualified</symbolName>(){ }

  @interface <symbolName descr="null" type="ANNOTATION_NAME">MyAnnotation</symbolName> { }
}
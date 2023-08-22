// "Replace method with delegate to super" "true-preview"
class ParentClass<T1> {
  void foo(T1 t1){
  }
}

class ChildForClass<R1 extends String> extends ParentClass<R1> {
  void foo<caret>(String t1) {
  }
}
// "Replace method with delegate to super" "true-preview"
class ParentClass<T1> {
  <T2> void foo(T1 t1, T2 t3){
  }
}

class ChildForClass<R1> extends ParentClass<R1> {
  <R2> void foo<caret>(R1 r1, R2 r2) {
  }
}
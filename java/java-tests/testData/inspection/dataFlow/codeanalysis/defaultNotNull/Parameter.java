import codeanalysis.experimental.annotations.DefaultNotNull;

@DefaultNotNull
class X {
  void m(X x) {}
  
  void use() {
    m(/*ca-nullable-to-not-null*/null);
  }
}
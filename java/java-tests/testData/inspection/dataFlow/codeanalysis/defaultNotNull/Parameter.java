import org.jspecify.annotations.DefaultNonNull;

@DefaultNonNull
class X {
  void m(X x) {}
  
  void use() {
    m(/*ca-nullable-to-not-null*/null);
  }
}
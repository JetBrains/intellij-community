import org.jspecify.nullness.NullMarked;

@NullMarked
class X {
  void m(X x) {}
  
  void use() {
    m(/*ca-nullable-to-not-null*/null);
  }
}
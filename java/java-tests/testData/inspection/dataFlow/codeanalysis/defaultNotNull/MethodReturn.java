import org.jspecify.annotations.NullMarked;

@NullMarked
class X {
  X get() {
    return /*ca-nullable-to-not-null*/null;
  }
}
import org.jspecify.nullness.NullMarked;

@NullMarked
class X {
  X get() {
    return /*ca-nullable-to-not-null*/null;
  }
}
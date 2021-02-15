import org.jspecify.annotations.DefaultNonNull;

@DefaultNonNull
class X {
  X get() {
    return /*ca-nullable-to-not-null*/null;
  }
}
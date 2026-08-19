// A wildcard type argument that has no upper bound of its own is still bounded by the declared bound of the
// corresponding type parameter. The containment rules of JLS 4.5.1 ignore this, but javac takes the declared
// bound into account, so 'Bounded<?>' is a subtype of 'Bounded<? extends CharSequence>' when the parameter of
// 'Bounded' is declared as '<X extends CharSequence>'.
import java.util.List;

interface BasicResult {}
interface BasicSet<R extends BasicResult> {}
interface BasicSession<R extends BasicResult, RS extends BasicSet<R>> {}
interface Result<R extends BasicResult, S extends BasicSession<R, ?>> {}

interface Bounded<X extends CharSequence> {}
interface Unbounded<X> {}

interface Set2<R extends CharSequence> {}
interface Session2<R extends CharSequence, RS extends Set2<R>> {}

class WildcardContainmentWithTypeParameterBound {
  static Result<?, ?> create() {
    return null;
  }

  void nestedCapture() {
    Result<? extends BasicResult, ? extends BasicSession<?, ? extends BasicSet<?>>> x = create();
  }

  void unboundedWildcard(List<Bounded<?>> src) {
    List<? extends Bounded<? extends CharSequence>> l = src;
  }

  void superBoundedWildcard(List<Bounded<? super String>> src) {
    List<? extends Bounded<? extends CharSequence>> l = src;
  }

  void noDeclaredBound(List<Unbounded<?>> src) {
    List<? extends Unbounded<? extends CharSequence>> l = <error descr="Incompatible types. Found: 'java.util.List<Unbounded<?>>', required: 'java.util.List<? extends Unbounded<? extends java.lang.CharSequence>>'">src</error>;
  }

  void declaredBoundIsTooWide(List<Bounded<?>> src) {
    List<? extends Bounded<? extends String>> l = <error descr="Incompatible types. Found: 'java.util.List<Bounded<?>>', required: 'java.util.List<? extends Bounded<? extends java.lang.String>>'">src</error>;
  }

  void substitutedBound(List<Session2<String, ?>> src) {
    List<? extends Session2<?, ? extends Set2<String>>> l = src;
  }
}

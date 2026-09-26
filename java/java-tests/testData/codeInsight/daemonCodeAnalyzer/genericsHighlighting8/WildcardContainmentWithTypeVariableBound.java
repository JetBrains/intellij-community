// The declared bound of a type parameter can be another type parameter of the same declaration. A wildcard type
// argument for that other parameter has no upper bound of its own, so the walk over the declared bounds must
// continue through it. javac accepts both assignments below.
// The negative cases of this rule are in WildcardContainmentWithTypeParameterBound.
import java.util.List;

interface StrangeDouble<R extends CharSequence, S extends R> {}

interface StrangeTriple<R extends CharSequence, S extends R, T extends S> {}

interface Cyclic<<error descr="Cyclic inheritance involving 'R'"></error>R extends S, S extends R> {}

class WildcardContainmentWithTypeVariableBound {
  void nested(List<StrangeDouble<?, ?>> src) {
    List<? extends StrangeDouble<?, ? extends CharSequence>> l = src;
  }

  void throughTwoParameters(List<StrangeTriple<?, ?, ?>> src) {
    List<? extends StrangeTriple<?, ?, ? extends CharSequence>> l = src;
  }

  // The walk stops on the cycle instead of looping
  void cyclicBounds(List<Cyclic<?, ?>> src) {
    List<? extends Cyclic<?, ? extends CharSequence>> l = <error descr="Incompatible types. Found: 'java.util.List<Cyclic<?,?>>', required: 'java.util.List<? extends Cyclic<?,? extends java.lang.CharSequence>>'">src</error>;
  }
}

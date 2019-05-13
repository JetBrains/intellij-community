import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

abstract class Constraint<ConstraintType extends Constraint<ConstraintType>> {

  protected abstract Stream<Event> evStream();

  private void foo(final Set<CConstraint> ctrlSTCs, 
                   final Set<BConstraint> probCstrs) {
    ArrayList<Event> a = new ArrayList<Event>(Stream
                                                .concat(ctrlSTCs.stream(), probCstrs.stream())
                                                .flatMap(Constraint::evStream)
                                                .collect(Collectors.toSet()));
  }

  private abstract class CConstraint extends Constraint<CConstraint> implements I {}
  private abstract class BConstraint extends Constraint<BConstraint> implements I {}

  interface I {}
  interface Event {}

}

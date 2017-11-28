package jetCheck;

import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;

/**
 * @author peter
 */
@SuppressWarnings("ExceptionClassNameDoesntEndWithException")
public class CannotSatisfyCondition extends RuntimeException {
  private final Predicate<?> condition;

  CannotSatisfyCondition(@NotNull Predicate<?> condition) {
    super("Cannot satisfy condition " + condition);
    this.condition = condition;
  }

  @NotNull
  public Predicate<?> getCondition() {
    return condition;
  }
}

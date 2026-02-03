import java.util.Date;
import java.util.concurrent.locks.Condition;

abstract class Demo6Impl implements Condition {
  @Override
  public boolean awaitUntil(Date <warning descr="Not annotated parameter overrides @NotNull parameter">deadline</warning>) {
    return false;
  }
}
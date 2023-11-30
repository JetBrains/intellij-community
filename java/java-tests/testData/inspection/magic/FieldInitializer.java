import org.intellij.lang.annotations.MagicConstant;

class TestAssignment {
  interface States {
    int INIT = 0;
    int RUNNING = 1;
    int STOPPED = 2;
  }
  @MagicConstant(valuesFromClass = States.class)
  int state = <warning descr="Should be one of: States.INIT, States.RUNNING, States.STOPPED">0</warning>;

  @MagicConstant(valuesFromClass = States.class)
  int state2 = States.RUNNING;

  public TestAssignment() {
    this.state = <warning descr="Should be one of: States.INIT, States.RUNNING, States.STOPPED">0</warning>;
  }
}
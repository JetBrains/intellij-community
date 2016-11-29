class BrokenAlignment {

  boolean smth() {
    if (<warning descr="Condition '2 == 2' is always 'true'">2 == 2</warning>) {
      return true;
    }

    boolean b = <warning descr="Condition '3 == 3' is always 'true'">3 == 3</warning>;

    return <warning descr="Condition '1 == 1' is always 'true'">1 == 1</warning>;
  }
}
public class ReassignInConstructor {
  int a = 1;
  int b = 2;

  public ReassignInConstructor() {
    <warning descr="Variable is already assigned to this value">b</warning> = 2;
  }
}
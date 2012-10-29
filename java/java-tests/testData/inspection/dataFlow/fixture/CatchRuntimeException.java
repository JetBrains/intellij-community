import org.jetbrains.annotations.NotNull;

public class BrokenAlignment {

  @NotNull
  Object test(){
    try{
      bar(<warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>);
      return <warning descr="'null' is returned by the method declared as @NotNull">null</warning>;
    }
    catch (RuntimeException e) {
      return null;
    }
  }

  public void bar(@NotNull Object foo) {
    assert <warning descr="Condition 'foo != null' is always 'true'">foo != null</warning>;
  }

  public void bar2(@NotNull Object foo) {
    assert <warning descr="Condition 'foo != null' is always 'true'">foo != null</warning>;
    try { }
    catch (java.lang.RuntimeException ex) { }
  }

}
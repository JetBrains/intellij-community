import java.util.Map;

class Main {
  private final Map<String, BreakpointState<?,?,?>> myBreakpointsDefaults = null;

  @SuppressWarnings("unchecked")
  public BreakpointState<String, String, String> foo1(String type, BreakpointState<?, ?, ?> defaults) {
    return (BreakpointState) myBreakpointsDefaults.computeIfAbsent(type, k -> defaults);
  }

  public BreakpointState foo2(String type, BreakpointState<?, ?, ?> defaults) {
    return (<warning descr="Casting 'myBreakpointsDefaults.computeIfAbsent(...)' to 'BreakpointState' is redundant">BreakpointState</warning>) myBreakpointsDefaults.computeIfAbsent(type, k -> defaults);
  }

  private static class BreakpointState<A, B, C> {}

}
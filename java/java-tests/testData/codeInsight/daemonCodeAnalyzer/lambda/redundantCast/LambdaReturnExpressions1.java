import java.util.Optional;

class CommandTest {
  public Object getObject() {
    Optional<Object> o = Optional.of("x");
    return o.map(bx -> {
                  return (Object)"";
                })
            .orElse(new Integer(1));
  }

  public Object getObject1() {
    Optional<Object> o = Optional.of("x");
    return o.map(bx -> {
                  return (<warning descr="Casting 'new Object()' to 'Object' is redundant">Object</warning>)new Object();
                })
            .orElse(new Integer(1));
  }
}

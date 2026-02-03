
import java.util.Arrays;
import java.util.List;

class Foo {
  private List<Foo> foos = Arrays.asList(new Foo("one"), new Foo("two"));

  private String name;

  public Foo(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }

  public List<Foo> getFoos() {
    return foos;
  }

  public Foo getFoo(String name) {
    if (name != null)
      <selection>for (Foo foo : getFoos())
        if (foo.getName().equals(name)) {
          return foo;
        }</selection>



    return null;
  }

}

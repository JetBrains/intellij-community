import java.util.Collection;

class Foo {
  public static final Foo[] EMPTY_ARRAY = new Foo[0];
  public static final Foo[] EMPTY_ARRAY2 = new Foo[]{};
  public static final Foo[] NON_EMPTY_ARRAY = new Foo[1];

  Collection<Foo> foos() {}

  {
    Foo[] f = foos().toArray(EMPTY_ARRAY);<caret>
  }

}

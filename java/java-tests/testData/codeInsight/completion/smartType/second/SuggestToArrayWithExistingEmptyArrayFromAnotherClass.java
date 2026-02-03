import java.util.Collection;

class Bar {
  public static final Bar[] EMPTY_ARRAY = new Bar[0];
  public static final Bar[] EMPTY_ARRAY2 = new Bar[]{};
  public static final Bar[] NON_EMPTY_ARRAY = new Bar[1];

}

class Foo {
  Collection<Bar> foos() {}

  {
    Bar[] f = f<caret>
  }
}

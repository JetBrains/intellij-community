// "Make 'Inner' protected" "true-preview"
@MyAnnotation(Outer.Inner.class)
public class Outer {

  protected static class Inner {

  }
}

@interface MyAnnotation {
  Class<?> value();
}
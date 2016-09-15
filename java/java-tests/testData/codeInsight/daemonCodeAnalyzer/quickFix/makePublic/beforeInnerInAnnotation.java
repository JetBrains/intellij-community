// "Make 'Inner' protected" "true"
@MyAnnotation(Outer.I<caret>nner.class)
public class Outer {

  private static class Inner {

  }
}

@interface MyAnnotation {
  Class<?> value();
}
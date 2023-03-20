// "Make 'Inner' protected" "true-preview"
@MyAnnotation(Outer.I<caret>nner.class)
public class Outer {

  private static class Inner {

  }
}

@interface MyAnnotation {
  Class<?> value();
}
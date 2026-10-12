import java.lang.annotation.*;

class ExtendsAnnotatedObject {

  @Target(ElementType.TYPE_USE)
  @Retention(RetentionPolicy.RUNTIME)
  @interface MyAnnotation {}

  static class MyClass extends @MyAnnotation Object {}

  public static void main(String[] args) {
    System.out.println(
      MyClass.class.getAnnotatedSuperclass().isAnnotationPresent(MyAnnotation.class));
  }
}
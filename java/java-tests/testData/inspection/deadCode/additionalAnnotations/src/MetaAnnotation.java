import java.lang.annotation.*;

// definition

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequestMapping {
  @SuppressWarnings("unused") // suppress warning as we test another thing
  RequestMethod[] method() default {};
}

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@RequestMapping(method = RequestMethod.GET)
@interface GetMapping {
}

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@RequestMapping(method = RequestMethod.POST)
@interface PostMapping {
}

enum RequestMethod {
  GET, POST
}

@interface Ann1 {}
@Ann1
@interface Ann2 {}
@Ann2
@interface Ann3 {}
@interface Ann4 {}

// test

class Controller {
  @GetMapping
  public int get() {
    return 111;
  }

  @PostMapping
  public void post() {
  }
}

@Ann3
class A {
}

@RequestMapping
class B {
}

@Ann4
class C {}
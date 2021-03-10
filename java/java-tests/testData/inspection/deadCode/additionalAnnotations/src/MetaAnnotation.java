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

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@interface Ann1 {
}

enum RequestMethod {
  GET, POST
}

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

@Ann1
class A {
}

@RequestMapping
class B {
}
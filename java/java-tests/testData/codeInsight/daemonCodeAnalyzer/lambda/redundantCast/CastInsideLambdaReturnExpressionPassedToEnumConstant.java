import java.util.function.Function;

interface Demo {

  enum X {

    A( key -> (<warning descr="Casting 'key.toString()' to 'String' is redundant">String</warning>) key.toString());

    X(Function<String, Object> function) {
      // ...
    }
  }

}
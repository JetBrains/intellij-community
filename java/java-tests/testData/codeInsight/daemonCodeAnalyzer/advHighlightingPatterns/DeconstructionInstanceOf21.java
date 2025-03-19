
public class DeconstructionInstanceOf20 {

  public interface Function<T, R> {
    R apply(T t);
  }

  public interface UnaryOperator<T> extends Function<T, T> {
  }

  class ClassMapper<T> implements UnaryOperator<T> {
    private T in;
    private T out;

    ClassMapper(T in, T out) {
      this.in = in;
      this.out = out;
    }

    public T apply(T arg) { return in.equals(arg) ? out : null; }
  }

  record Mapper<T>(T in, T out) implements UnaryOperator<T> {
    public T apply(T arg) { return in.equals(arg) ? out : null; }
  }

  void test(UnaryOperator<? extends CharSequence> op, Object ob, Mapper<? extends String> mapperString) {
    //Example 18.5.5-1. Record Pattern Type Inference JLS with Record Patterns (Second Preview)
    if (op instanceof Mapper(var in, var out)) {
      boolean shorter = out.length() < in.length();
    }
    if (mapperString instanceof Mapper<? extends CharSequence>(var in, var out)) {
      boolean shorter = out.length() < in.length();
    }

    if (op instanceof Mapper<?>(var in, var out)) {
      System.out.println(in.hashCode());
    }
    if (op instanceof Mapper<? extends CharSequence>(var in, var out)) {
      System.out.println(in.hashCode());
    }
    if (op instanceof Mapper<? extends Object>(var in, var out)) {
      System.out.println(in.hashCode());
    }
    if (op instanceof Mapper(var in, var out)) {
      System.out.println(in.hashCode());
    }
    if (op instanceof Mapper<? extends CharSequence> m) {
      System.out.println(m);
    }

    if (op instanceof Mapper m) {
      System.out.println(m);
    }

    if (op instanceof ClassMapper<? extends CharSequence> m) {
      System.out.println(m);
    }
    //error
    if (<error descr="Inconvertible types; cannot cast 'DeconstructionInstanceOf20.UnaryOperator<capture<? extends java.lang.CharSequence>>' to 'DeconstructionInstanceOf20.Mapper<java.lang.Object>'">op instanceof Mapper<Object> m</error>) {
      System.out.println(m);
    }
    //error
    if (ob instanceof <error descr="'Object' cannot be safely cast to 'UnaryOperator<Object>'">UnaryOperator<Object> objectUnaryOperator</error>) {

    }
    //error
    if (ob instanceof <error descr="'Object' cannot be safely cast to 'UnaryOperator<Object>'">UnaryOperator<Object> objectUnaryOperator</error>) {

    }
    if (op instanceof Mapper objectUnaryOperator) {

    }
  }

  record Pair<T,T1>(T first, T1 second) {};

  public static void notCheckcastConvertible(){
    Object o = "Some string";
    if (o instanceof Pair(Integer x, String y)) {
      System.out.println(x + " " + y);
    } else {
      System.out.println("notCheckcastConvertible default branch");
    }
  };

  public static void checkcastConvertible(){
    Pair<Integer, String> pair = new Pair<>(42, "hello");

    Object o = pair;
    if (o instanceof Pair(Integer x, String y)) {
      System.out.println(x + " " + y);
    } else {
      System.out.println("notCheckcastConvertible default branch");
    }
  };
}
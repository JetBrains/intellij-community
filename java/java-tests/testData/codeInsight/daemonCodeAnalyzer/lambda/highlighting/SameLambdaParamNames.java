import java.util.function.BiFunction;
class X {
  BiFunction<Object, Object, Object> b = (<error descr="Variable 'o1' is already defined in the scope">o1</error>, <error descr="Variable 'o1' is already defined in the scope">o1</error>) -> null;
}
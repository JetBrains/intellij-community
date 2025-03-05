class LambdaConv10 {

  interface I<T, R> { public R call( T t); }

  {
    I<Integer,Integer> in = (<error descr="Incompatible parameter type in lambda expression: expected Integer but found int">int i</error>) -> 2 * i;
  }
}

class Test {
  public static <Tfoo, Vfoo> Future<Vfoo> foo(Future<Tfoo> future, Function<Tfoo, Vfoo> function) {
    return future.map(function);
  }

  // These interfaces inspired by FoundationDB Java client class files
  interface PartialFunction <TP, VP> {
    VP apply(TP t) throws java.lang.Exception;
  }

  interface Function <TF, VF> extends PartialFunction<TF, VF> {
    VF apply(TF t);
  }

  interface PartialFuture <TPP> {
    <VPP> PartialFuture<VPP> map(PartialFunction<TPP, VPP> partialFunction);
  }

  interface Future <TFF> extends PartialFuture<TFF> {
    <VFF> Future<VFF> map(Function<TFF, VFF> function);
  }
}

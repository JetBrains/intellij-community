
abstract class WrapperOne<T> {
  public abstract <X> WrapperOne<X> reformChain(Reformer<? extends WrapperTwo<? extends X>> reformer);
}
interface WrapperTwo<T> {}
interface Reformer<T> {
  T reform();
}
class ReformerClient {
  public WrapperOne<String> sampleChainA(WrapperOne<String> p, Reformer<WrapperTwo<String>> r) {
    return p.reformChain(r::reform);
  }

  public WrapperOne<String> sampleChainB(WrapperOne<String> p, Reformer<? extends WrapperTwo<String>> r) {
    return p.reformChain(r::reform);
  }

  public WrapperOne<String> sampleChainC(WrapperOne<String> p, Reformer<? extends WrapperTwo<String>> r) {
    return p.reformChain(r);
  }
}
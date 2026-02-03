import a.Provider;

public abstract class Test<T> {
  private void configure(Provider<T> provider,
                         Test<? super T> sBind,
                         Test<? extends T> eBind) {
    sBind.toProvider(provider);
    eBind.toProvider<error descr="Cannot resolve method 'toProvider(Provider<T>)'">(provider)</error><EOLError descr="';' expected"></EOLError>
  }

  abstract void toProvider(Provider<? extends T> var1);
  abstract void toProvider(b.Provider<? extends T> var1);
}
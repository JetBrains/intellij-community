import java.util.function.Function;

class BadReturnType {
  public static  <T, S> S doIfNotNull(T obj, Function<? super T, ? extends S> function) {
    return obj == null ? null : function.apply(obj);
  }

  interface Stub<Psi> {
    Psi get();
  }
  interface MetadataStub<M extends Metadata> extends Stub<M> {}
  interface Metadata {}

  Metadata m(MetadataStub member){
    Metadata m = (Metadata) doIfNotNull(member, MetadataStub::get);
    bar( (Metadata) doIfNotNull(member, metadataStub -> metadataStub.get()));
    bar( (Metadata) doIfNotNull(member, MetadataStub::get));
    bar( (<warning descr="Casting 'doIfNotNull(...)' to 'Metadata' is redundant">Metadata</warning>)  doIfNotNull  (member, null));
    return (Metadata) doIfNotNull(member, MetadataStub::get);
  }

  private void bar(Metadata md) { }

}

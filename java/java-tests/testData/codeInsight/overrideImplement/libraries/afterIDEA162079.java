import p.P;

interface A extends P<String> {
    @java.lang.Override
    <S extends String> void m(java.util.List<S> list);
}
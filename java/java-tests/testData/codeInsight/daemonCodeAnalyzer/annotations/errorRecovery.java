class Foo {

  @<error descr="Cannot resolve symbol 'Deprec'">Deprec</error>(<error descr="Cannot resolve symbol 'ated'">ated</error><EOLError descr="',' or ')' expected"></EOLError>
    void a() {}

    void b() {}

    void c() {}
}
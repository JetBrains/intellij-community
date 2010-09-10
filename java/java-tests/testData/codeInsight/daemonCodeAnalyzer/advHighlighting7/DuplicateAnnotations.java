class C{
    @<error descr="Duplicate annotation">Deprecated</error> @<error descr="Duplicate annotation">Deprecated</error>
    void foo(@<error descr="Duplicate annotation">Deprecated</error> @<error descr="Duplicate annotation">Deprecated</error> int x){}
}

// Ambiguous method call

class C61 {
	public void foo(String s) {}
	public void foo(Integer i) {}
	public void foo2() {
		foo<error descr="Ambiguous method call: both 'C61.foo(String)' and 'C61.foo(Integer)' match">(null)</error>;
	}
}

class D61 extends C61 {
	public void foo(Integer i) {}
	public void foo2() {
		foo<error descr="Ambiguous method call: both 'D61.foo(Integer)' and 'C61.foo(String)' match">(null)</error>;
		foo<error descr="Cannot resolve method 'foo(int)'">(1)</error>;
	}
}
class ex {
    void f(String name, String[] i){}
    void f(String name, ex i){}

    void g() {
        f<error descr="Ambiguous method call: both 'ex.f(String, String[])' and 'ex.f(String, ex)' match">("",null)</error>;
    }
}

class XX {
    XX() {}
    void XX() {}

    {
        new XX().XX();
    }
}


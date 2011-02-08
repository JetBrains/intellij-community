class A{
	interface A{}
	interface B{}

	class C implements A, B{
		void foo(A a){}
		void foo(B b){}
	}
	{
		new C().<ref>foo(new C())
	}
}

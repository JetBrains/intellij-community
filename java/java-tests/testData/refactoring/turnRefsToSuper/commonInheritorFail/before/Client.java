class Client
{

    interface L { void m (); }

    class V implements L { 
	void m () { }
	void q () { }
    }

    //------------------------------------
 
    class C  {
	int method(V v) { v.q(); return 0; }
    }
    
    interface I { int method(V v); }

    class D extends C implements I { }
}

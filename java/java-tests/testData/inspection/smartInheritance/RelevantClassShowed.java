class MyInheritor implemen<caret>ts A {

}

//first level

interface A {
}

//2 level

class B implements A {
}

//3 level

class C extends B {
}

class C1 extends B {
}

//...

class D extends C{}
class D1 extends C{}
class D2 extends C{}
class D3 extends C{}
class D4 extends C{}
class D5 extends C{}
class D6 extends C{}
class D7 extends C{}
class D8 extends C{}

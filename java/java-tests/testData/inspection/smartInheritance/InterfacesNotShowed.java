class MyInheritor implement<caret>s A {

}

interface A {
}

interface B extends A {}
interface B1 extends A {}
interface B6 extends A {}
interface B2 extends A {}
interface B3 extends A {}
interface B4 extends A {}
interface B5 extends A {}

interface C extends B {}
interface C1 extends B {}
interface C2 extends B {}
interface C3 extends B {}
interface C4 extends B {}

class D extends C {}

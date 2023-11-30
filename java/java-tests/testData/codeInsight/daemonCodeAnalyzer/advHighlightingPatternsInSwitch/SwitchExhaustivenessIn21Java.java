import java.util.Optional;

record PrimitiveAndSealed(int x, I y) {}
record NormalAndSealed(Integer x, I y) {}


sealed interface Super permits SuperChild, SuperRecord {}
final class SuperChild implements Super {}
record SuperRecord(I i) implements Super {}

record Generic<T>(T x) {}

class A {}
class B extends A {}

sealed interface I permits C, D {}
final class C implements I {}
final class D implements I {}
record Pair<T>(T x, T y) {}

record Top(Child c1, Child c2) {}
record Child(I x, I y){}

class Basic {

  String o;
  Super superInterface;
  SuperRecord superRecord;
  PrimitiveAndSealed primitiveAndSealed;
  NormalAndSealed normalAndSealed;
  Generic<I> genericInterface;
  Generic<C> genericC;

  <T extends Super> void testGenerics(T p, Pair<T> pair1, Pair<? extends Super> pair2) {
    switch (p) {
      case SuperChild superChild -> {}
      case SuperRecord(C i) -> {}
      case SuperRecord(D i) -> {}
    }
    switch(p) {
      case SuperChild sc -> {}
      case SuperRecord sr -> {}
    }
    switch (pair1.x()) {
      case SuperChild sc -> {}
      case SuperRecord sr -> {}
    }
    switch (pair2.x()) {
      case SuperChild sc -> {}
      case SuperRecord sr -> {}
    }
  }

  void test(){

    switch (superInterface) { //completed sealed with record
      case SuperChild superChild -> {}
      case SuperRecord(C i) -> {}
      case SuperRecord(D i) -> {}
    }
    switch (superInterface) {
      case SuperChild superChild -> {}
      case SuperRecord(C i) -> {}
      case SuperRecord(I i) -> {}
    }
    switch (<error descr="'switch' statement does not cover all possible input values">superInterface</error>) {
      case SuperChild superChild -> {}
      case SuperRecord(C i) -> {}
      case SuperRecord(I i) when i.hashCode() > 0 -> {}
    }
    switch (o) { //non-record is completed
      case String o -> {}
    }
    switch (<error descr="'switch' statement does not cover all possible input values">superRecord</error>) {
      case SuperRecord(C i) -> {}
    }
    switch (superRecord) {
      case SuperRecord(I i) -> {}
    }
    switch (<error descr="'switch' statement does not cover all possible input values">primitiveAndSealed</error>){ //error
      case PrimitiveAndSealed(int x, C y) -> {}
    }
    switch (primitiveAndSealed){
      case PrimitiveAndSealed(int x, I y) -> {}
    }
    switch (primitiveAndSealed){
      case PrimitiveAndSealed(int x, C y) -> {}
      case PrimitiveAndSealed(int x, D y) -> {}
    }
    switch (<error descr="'switch' statement does not cover all possible input values">normalAndSealed</error>){ //error
      case NormalAndSealed(Integer x, C y) -> {}
    }
    switch (normalAndSealed){
      case NormalAndSealed(Integer x, I y) -> {}
    }
    switch (normalAndSealed){
      case NormalAndSealed(Integer x, C y) -> {}
      case NormalAndSealed(Integer x, D y) -> {}
    }
    switch (genericInterface) {
      case Generic<I>(I i) -> {}
    }
    switch (<error descr="'switch' statement does not cover all possible input values">genericInterface</error>) {
      case Generic<I>(C i) -> {}
    }
    switch (genericInterface) {
      case Generic<I>(C i) -> {}
      case Generic<I>(D i) -> {}
    }
    switch (genericC) {
      case Generic<C>(C i) -> {}
    }
  }

  void testNested(Top t){
    switch (<error descr="'switch' statement does not cover all possible input values">t</error>){
      case Top(Child(I x1, C y1), Child(C x2, I y2)) -> {}
      case Top(Child(I x1, D y1) , Child(I x2, C y2)) -> {}
    }
    switch (t){
      case Top(Child(I x1, C y1) , Child c2) -> {}
      case Top(Child(I x1, D y1) , Child c2) -> {}
    }
    switch (t){
      case Top(Child(I x1, C y1) , Child(I x2, I y2) ) -> {}
      case Top(Child(I x1, D y1) , Child(I x2, I y2) ) -> {}
    }
    switch (<error descr="'switch' statement does not cover all possible input values">t</error>){
      case Top(Child c1, Child(C x2, I y2) ) -> {}
      case Top(Child c1, Child(I x2, C y2) ) -> {}
    }
    switch (<error descr="'switch' statement does not cover all possible input values">t</error>){
      case Top(Child(I x1, C y1) , Child(I x2, I y2) ) -> {}
      case Top(Child(C x1, I y1) , Child(I x2, I y2) ) -> {}
    }
    switch (t){
      case Top(Child(I x1, I y1) , Child(I x2, I y2) ) -> {}
    }
  }

  void test(Pair<A> pairA, Pair<I> pairI) {

    switch (<error descr="'switch' statement does not cover all possible input values">pairA</error>) {                 // Error!
      case Pair<A>(A a,B b) -> { }
      case Pair<A>(B b,A a) -> { }
    }

    switch (<error descr="'switch' statement does not cover all possible input values">pairI</error>) {                 // Error!
      case Pair<I>(C fst, D snd) -> {}
      case Pair<I>(D fst, C snd) -> {}
      case Pair<I>(I fst, C snd) -> {}
    }

    switch (pairI) {
      case Pair<I>(I i,C c) -> { }
      case Pair<I>(I i,D d) -> { }
    }

    switch (pairI) {
      case Pair<I>(C c,C i) -> { }
      case Pair<I>(C c,D i) -> { }
      case Pair<I>(D d,C c) -> { }
      case Pair<I>(D d,D d2) -> { }
    }

    switch (pairI) {
      case Pair<I>(C c,I i) -> { }
      case Pair<I>(D d,C c) -> { }
      case Pair<I>(D d,D d2) -> { }
    }

    switch(pairI.x()) {
      case C c when true -> {}
      case D d -> {}
    }
  }

  record R(int x) {
  }

  void emptyRecord(R r) {
    switch (<error descr="'switch' statement does not have any case clauses">r</error>) { //error
    }
  }
  void emptyRecord2(R r) {
    switch (<error descr="'switch' statement does not cover all possible input values">r</error>) { //error
      case null -> System.out.println("1");
    }
  }
  void emptyRecord3(R r) {
    switch (<error descr="'switch' statement does not cover all possible input values">r</error>) { //error
      case R r2 when r2.x() == 1 -> System.out.println("1");
    }
  }
  void emptyInteger(int i) {
    switch (i) {
    }
  }

  void emptyObject0(Object o) {
    switch (<error descr="'switch' statement does not have any case clauses">o</error>) {  //error
    }
  }

  void emptyObject(Object o) {
    switch (<error descr="'switch' statement does not cover all possible input values">o</error>) {  //error
      case null -> System.out.println(1);
    }
  }

  void emptyObject2(Object o) {
    switch (<error descr="'switch' statement does not cover all possible input values">o</error>) {
      case String s when s.length()==1 -> System.out.println(1);
    }
  }

  void emptyRecord4(R r) {
    switch (r) {
      case R r2 when r2.x() == 1  -> System.out.println(1);
      case R r2 -> System.out.println("1");
    }
  }
  void emptyRecord5(R r) {
    switch (<error descr="'switch' statement does not cover all possible input values">r</error>) { //error
      case R r2 when r2.x() == 1  -> System.out.println(1);
    }
  }

  void emptyRecord6(R r) {
    switch (r) {
      default -> System.out.println("1");
    }
  }
  //see com.intellij.codeInsight.daemon.impl.analysis.PatternHighlightingModel.reduceRecordPatterns
  void exhaustinvenessWithInterface(Pair<I> pairI) {
    switch (<error descr="'switch' statement does not cover all possible input values">pairI</error>) {
      case Pair<I>(C fst, D snd) -> {}
      case Pair<I>(I fst, C snd) -> {}
      case Pair<I>(D fst, I snd) -> {}
    }
  }

  //see com.intellij.codeInsight.daemon.impl.analysis.PatternHighlightingModel.reduceRecordPatterns
  void exhaustinvenessWithInterface2(Pair<? extends I> pairI) {
    switch (<error descr="'switch' statement does not cover all possible input values">pairI</error>) {
      case Pair<? extends I>(C fst, D snd) -> {}
      case Pair<? extends I>(I fst, C snd) -> {}
      case Pair<? extends I>(D fst, I snd) -> {}
    }
  }

  sealed interface Parent {}
  record AAA() implements Parent {}
  record BBB() implements Parent {}

  void test(Optional<? extends Parent> optional) {
    switch (optional.get()) {
      case AAA a -> {}
      case BBB b -> {}
    }
  }


  class CCCC{}
  public <T extends CCCC & Comparable<T>> void test(T c) {
    switch (c) {
      case Comparable t -> System.out.println(13);
    }
  }


  interface II1{}
  sealed interface II2{}
  non-sealed class Cl1 implements II2{}
  public <T extends II1 & II2> void test(T c) {
    switch (c) {
      case Cl1 t:
        System.out.println(21);
        ;
    }
  }


  sealed interface J permits En, FC {
  }

  enum En implements J {A, B}

  final class FC implements J {
  }

  static int testExhaustive2(J ji) {
    return switch (ji) {
      case FC c -> 42;
      case En.A -> 0;
      case En.B -> 0;
    };
  }


  record PairT(T t1, T t2) {
  }

  sealed interface T {
  }

  sealed interface T1 extends T {
  }

  sealed interface T2 extends T {
  }

  record T11(T t1, T t2) implements T1 {
  }

  record T12(T t1, T t2) implements T1 {
  }

  record T21(T t1, T t2) implements T2 {
  }

  record T22(T t1, T t2) implements T2 {
  }

  public void test1(PairT pairT) {
    switch (pairT) {
      case PairT(T11 t1, T1 t2) -> System.out.println(1);
      case PairT(T12 t1, T1 t2) -> System.out.println(1);
      case PairT(T1 t1, T2 t2) -> System.out.println(1);
      case PairT(T2 t1, T t2) -> System.out.println(1);
    }
  }

  public void test2(PairT pairT) {
    switch (<error descr="'switch' statement does not cover all possible input values">pairT</error>) { //error
      case PairT(T11 t1, T1 t2) -> System.out.println(1);
      case PairT(T12 t1, T1 t2) -> System.out.println(1);
      case PairT(T2 t1, T t2) -> System.out.println(1);
    }
  }

  public void test3(PairT pairT) {
    switch (pairT) {
      case PairT(T1 t1, T11 t2) -> System.out.println(1);
      case PairT(T1 t1, T12 t2) -> System.out.println(1);
      case PairT(T2 t1, T1 t2) -> System.out.println(1);
      case PairT(T t1, T2 t2) -> System.out.println(1);
    }
  }

  public void test4(PairT pairT) {
    switch (pairT) {
      case PairT(T1 t1, T11(T1 t12, T2 t22)) -> System.out.println(53714);
      case PairT(T1 t1, T11(T2 t12, T2 t22)) -> System.out.println(1);
      case PairT(T1 t1, T11(T t12, T1 t22)) -> System.out.println(1);
      case PairT(T1 t1, T12 t2) -> System.out.println(1);
      case PairT(T2 t1, T1 t2) -> System.out.println(1);
      case PairT(T t1, T2 t2) -> System.out.println(1);
    }
  }
  public void test5(PairT pairT) {
    switch (<error descr="'switch' statement does not cover all possible input values">pairT</error>) {//error
      case PairT(T1 t1, T11(T1 t12, T2 t22)) -> System.out.println(53714);
      case PairT(T1 t1, T11(T t12, T1 t22)) -> System.out.println(1);
      case PairT(T1 t1, T12 t2) -> System.out.println(1);
      case PairT(T2 t1, T1 t2) -> System.out.println(1);
      case PairT(T t1, T2 t2) -> System.out.println(1);
    }
  }

  public void test6(PairT pairT) {
    switch (pairT) {
      case PairT(T1 t1, T11(T1 t12, T2 t22)) -> System.out.println(53714);
      case PairT(T1 t1, T11(T2 t12, T2 t22)) -> System.out.println(1);
      case PairT(T1 t1, T11(T t12, T1 t22)) -> System.out.println(1);
      case PairT(T1 t1, T12(T1 t12, T t22)) -> System.out.println(1);
      case PairT(T1 t1, T12(T2 t12, T t22)) -> System.out.println(1);
      case PairT(T2 t1, T1 t2) -> System.out.println(1);
      case PairT(T t1, T2 t2) -> System.out.println(1);
    }
  }
  public void test7(PairT pairT) {
    switch (pairT) {
      case PairT(T11(T1 t12, T2 t22), T1 t1) -> System.out.println(53714);
      case PairT(T11(T2 t12, T2 t22), T1 t1) -> System.out.println(1);
      case PairT(T11(T t12, T1 t22), T1 t1) -> System.out.println(1);
      case PairT(T12(T1 t12, T t22), T1 t1) -> System.out.println(1);
      case PairT(T12(T2 t12, T t22), T1 t1) -> System.out.println(1);
      case PairT( T1 t2, T2 t1) -> System.out.println(1);
      case PairT( T2 t2, T t1) -> System.out.println(1);
    }
  }
  public void test8(PairT pairT) {
    switch (pairT) {
      case PairT(T11(T1 t12, T2 t22), T1 t1) -> System.out.println(53714);
      case PairT(T11(T2 t12, T2 t22), T1 t1) -> System.out.println(1);
      case PairT(T11(T t12, T1 t22), T1 t1) -> System.out.println(1);
      case PairT(T12(T1 t12, T t22), T1 t1) -> System.out.println(1);
      case PairT(T12(T2 t12, T t22), T1 t1) -> System.out.println(1);
      case PairT( T1 t2, T21 t1) -> System.out.println(1);
      case PairT( T1 t2, T22(T t11, T1 t12)) -> System.out.println(1);
      case PairT( T1 t2, T22(T t11, T2 t12)) -> System.out.println(1);
      case PairT( T2 t2, T t1) -> System.out.println(1);
    }
  }

  public void test9(PairT pairT) {
    switch (<error descr="'switch' statement does not cover all possible input values">pairT</error>) {
      case PairT(T11(T1 t12, T2 t22), T1 t1) when ((Object)pairT).hashCode()==1 -> System.out.println(53714);
      case PairT(T11(T2 t12, T2 t22), T1 t1) -> System.out.println(1);
      case PairT(T11(T t12, T1 t22), T1 t1) -> System.out.println(1);
      case PairT(T12(T1 t12, T t22), T1 t1) -> System.out.println(1);
      case PairT(T12(T2 t12, T t22), T1 t1) -> System.out.println(1);
      case PairT( T1 t2, T21 t1) -> System.out.println(1);
      case PairT( T1 t2, T22(T t11, T1 t12)) -> System.out.println(1);
      case PairT( T1 t2, T22(T t11, T2 t12)) -> System.out.println(1);
      case PairT( T2 t2, T t1) -> System.out.println(1);
    }
  }



  sealed interface TInt {}
  final class TI1 implements TInt {}
  final class TI2 implements TInt {}
  record RR(String s, TInt i) {}
  void test(RR r) {
    switch (r) {
      case RR(CharSequence c1, TI1 c2) -> System.out.println("1");
      case RR(Object c1, TI2 c2) -> System.out.println("2");
    }
  }

  class OnlyDirectChildren{
    sealed interface T permits T1, T2, T3 {}
    sealed interface T1 extends T permits T12 {}
    sealed class T2 implements T {}
    sealed interface T3 extends T {}
    final class T12 extends T2 implements T1 {}
    final class T13 implements T3 {}

    void test(T i) {
      switch (<error descr="'switch' statement does not cover all possible input values">i</error>) {
        case T2 i2 ->
          System.out.println("2");
        case T13 i3 ->
          System.out.println("3");
      }
    }
    void test2(T i) {
      switch (i) {
        case T2 i2 ->
          System.out.println("2");
        case T1 i1 ->
          System.out.println("1");
        case T13 i3 ->
          System.out.println("3");
      }
    }
  }

  class Intersection{


      sealed interface T1 {
      }

      sealed interface T2 {
      }

      final class T11 implements T1 {
      }

      final class T12 implements T2 {
      }

      final class T112 implements T1, T2 {
      }

      record Pair<L>(L a){}
      <A extends T1 & T2> void test(A z) {
        switch (z) {
          case T112 c -> System.out.println("1");
        }
      }

      <A extends T1 & T2> void test(Pair<A> z) {
        switch (z) {
          case Pair(T112  c) -> System.out.println("23875");
        }
      }
  }

  class EmptyStatement(){
    sealed class A {}
    final class AA extends A {}
    sealed class AB extends A {}
    non-sealed class AC extends A {}
    final class ABA extends AB {}
    non-sealed class ABC extends AB {}

      void test(A a) {
        switch (<error descr="'switch' statement does not have any case clauses">a</error>) {
        }
    }
  }
}
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

public class Basic {

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
      case SuperRecord(I i) r when i.hashCode() > 0 -> {}
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
      case Top(Child(I x1, C y1) c1, Child(C x2, I y2) c2) -> {}
      case Top(Child(I x1, D y1) c1, Child(I x2, C y2) c2) -> {}
    }
    switch (t){
      case Top(Child(I x1, C y1) c1, Child c2) -> {}
      case Top(Child(I x1, D y1) c1, Child c2) -> {}
    }
    switch (t){
      case Top(Child(I x1, C y1) c1, Child(I x2, I y2) c2) -> {}
      case Top(Child(I x1, D y1) c1, Child(I x2, I y2) c2) -> {}
    }
    switch (<error descr="'switch' statement does not cover all possible input values">t</error>){
      case Top(Child c1, Child(C x2, I y2) c2) -> {}
      case Top(Child c1, Child(I x2, C y2) c2) -> {}
    }
    switch (<error descr="'switch' statement does not cover all possible input values">t</error>){
      case Top(Child(I x1, C y1) c1, Child(I x2, I y2) c2) -> {}
      case Top(Child(C x1, I y1) c1, Child(I x2, I y2) c2) -> {}
    }
    switch (t){
      case Top(Child(I x1, I y1) c1, Child(I x2, I y2) c2) -> {}
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
  }

}
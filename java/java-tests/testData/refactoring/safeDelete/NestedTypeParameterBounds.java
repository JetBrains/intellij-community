
class MyTest {
  interface N<<caret>X> {}
  N<? super N<? super N<? super N>>> k;
}

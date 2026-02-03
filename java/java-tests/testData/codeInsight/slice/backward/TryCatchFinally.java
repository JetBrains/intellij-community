package x;

class X {
  void f(Throwable p) {
    Throwable error = <flown1>null;

    try {
      f(p);
    }
    catch (Throwable <flown21>e) {
      error = <flown2>e;
    }
    finally {
      f(<caret>error);
    }
  }
}
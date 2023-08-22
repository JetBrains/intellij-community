enum E {
  W, L, M;

  E getCurrent(boolean b1, boolean b2) {
    E e;
    if (b1) {
        if (b2) e = W;
        else e = M;
    }
    e = b2 ? W : L;
  }
}
package p;

interface Board {
    int getCount();
}
class BoardImpl implements Board {
    @Override
    public int getCount() {return 0;}
}
class King<B extends Board> {
  boolean isLast1(B board) {
    return board.getCount() == 1;
  }
  boolean isLast2(java.util.List<? extends Board> list) {
      return list.get(0).getCount() == 1;
  }
}

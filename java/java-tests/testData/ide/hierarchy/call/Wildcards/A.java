package p;

interface Board {
    int getCount();
}
class BoardImpl implements Board {
    @Override
    public int getCount() {return 0;}
}
class King<B extends Board> {
    B board;
    boolean isLast() {
        return board.getCount() == 1;
    }
}

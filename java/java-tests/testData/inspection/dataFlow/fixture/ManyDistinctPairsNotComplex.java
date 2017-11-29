public class ManyDistinctPairsNotComplex {
  enum SquareType {
    BLACKROOK, BLACKKNIGHT, BLACKBISHOP, BLACKKING, BLACKQUEEN, BLACKPAWN
  }

  private SquareType[][] squares;
  private int WIDTH;
  private int HEIGHT;

  public void blackStartPos() {
    for (int row = 0; row < HEIGHT; row++) {
      for (int col = 0; col < WIDTH; col++) {
        if (row == 6) {
          squares[row][col] = SquareType.BLACKPAWN;
        } else if (row == 7 && (col == 0 || col == 7)) {
          squares[row][col] = SquareType.BLACKROOK;
        } else if (row == 7 && (col == 1 || col == 6)) {
          squares[row][col] = SquareType.BLACKKNIGHT;
        } else if (row == 7 && (col == 2 || col == 5)) {
          squares[row][col] = SquareType.BLACKBISHOP;
        } else if (row == 7 && col == 3) {
          squares[row][col] = SquareType.BLACKKING;
        } else if (row == 7 && col == 4) {
          squares[row][col] = SquareType.BLACKQUEEN;
        } else if (<warning descr="Condition 'row == 7 && col == 2' is always 'false'">row == 7 && <warning descr="Condition 'col == 2' is always 'false' when reached">col == 2</warning></warning>) {
          System.out.println("Impossible");
        }
      }
    }
  }
}

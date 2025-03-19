import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class Main {
  public static void main(String[] args) {
    char[][] field = new char[10][10];
    for (char[] chars : field) {
      Arrays.fill(chars, '.');
    }

    //noinspection ExtractMethodRecommender
    List<String> list = new ArrayList<>();
    int numRows = field.length;
    int numCols = field[0].length;
    for (int row = 0; row < numRows; row++) {
      if (field[row][0] == '.') {
        list.add("Empty first cell at row " + row);
      }
    }
    for (int col = 0; col < numCols; col++) {
      if (field[0][col] == '.') {
        list.add("Empty first cell at col " + col);
      }
    }

    for (String dummyText : list) {
      // More dummy stuff to trigger warning
      System.out.println(dummyText);
      System.out.println("Dummy stuff to trigger the warning");
    }
  }
}
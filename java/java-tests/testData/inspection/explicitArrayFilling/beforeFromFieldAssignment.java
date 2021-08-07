// "Replace loop with 'Arrays.fill()' method call" "true"
package pack;

public class TableWrapper {

  int[] table;

  public void clear() {
    int[] tab;
    if ((tab = table) != null && table.length > 0) {
      for (int<caret> i = 0; i < tab.length; ++i) {
        tab[i] = 0;
      }
    }
  }
}
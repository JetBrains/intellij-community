// "Replace loop with 'Arrays.fill()' method call" "true"
package pack;

import java.util.Arrays;

public class TableWrapper {

  int[] table;

  public void clear() {
    int[] tab;
    if ((tab = table) != null && table.length > 0) {
        Arrays.fill(tab, 0);
    }
  }
}
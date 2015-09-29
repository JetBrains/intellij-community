class Test {
  public interface JTableColumn<E, J> {}

  public interface JTableEntity<E> extends Comparable<E> {}

  public class JTableModel<E extends JTableEntity<E>, J extends JTable, T extends Enum<T> & JTableColumn<E, J>> extends AbstractTableModel {
    @Override public int getRowCount() {
      return 0;
    }

    @Override public int getColumnCount() {
      return 0;
    }

    @Override public Object getValueAt(int rowIndex, int columnIndex) {
      return null;
    }

    void doStuff() {
      // Do stuff
    }
  }

  interface JTable {

    AbstractTableModel getModel();
  }

  static class AbstractTableModel {
    public int getRowCount() {
      return 0;
    }

    public int getColumnCount() {
      return 0;
    }

    public Object getValueAt(int rowIndex, int columnIndex) {
      return null;
    }

  }

  @SuppressWarnings("unchecked")
  public static <T extends Enum<T> & JTableColumn<?, ?>> void setupTable(JTable table) {
    ((JTableModel<?, ?, T>) table.getModel()).doStuff();
  }
}
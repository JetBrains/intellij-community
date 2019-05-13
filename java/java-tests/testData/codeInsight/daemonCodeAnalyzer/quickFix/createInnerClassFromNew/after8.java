// "Create inner class 'MyTableModel'" "true"

public class Test {
    public static void main() {
        JTable table = new JTable(new MyTableModel());
    }

    private static class MyTableModel implements TableModel {
    }
}
class JTable {
  JTable(TableModel t) {}
}
interface TableModel {}
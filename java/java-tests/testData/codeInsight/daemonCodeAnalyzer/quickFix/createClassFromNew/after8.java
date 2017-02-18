// "Create class 'MyTableModel'" "true"

public class Test {
    public static void main() {
        JTable table = new JTable(new MyTableModel());
    }
}
class JTable {
  JTable(TableModel t) {}
}
interface TableModel {}

public class <caret>MyTableModel implements TableModel {
}
// "Create inner class 'MyTableModel'" "true"

public class Test {
    public static void main() {
        JTable table = new JTable(new MyTable<caret>Model());
    }
}
class JTable {
  JTable(TableModel t) {}
}
interface TableModel {}
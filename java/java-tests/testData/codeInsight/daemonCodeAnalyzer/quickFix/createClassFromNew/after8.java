// "Create Class 'MyTableModel'" "true"
import javax.swing.*;
import javax.swing.table.TableModel;

public class Test {
    public static void main() {
        JTable table = new JTable(new MyTableModel());
    }
}<caret>

public class MyTableModel implements TableModel {
}
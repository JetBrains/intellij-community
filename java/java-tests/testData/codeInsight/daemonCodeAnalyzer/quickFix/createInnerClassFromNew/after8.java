// "Create inner class 'MyTableModel'" "true"
import javax.swing.*;
import javax.swing.table.TableModel;

public class Test {
    public static void main() {
        JTable table = new JTable(new MyTableModel());
    }

    private static class MyTableModel implements TableModel {
    }
}
// "Create class 'MyTableModel'" "true"
import javax.swing.*;

public class Test {
    public static void main() {
        JTable table = new JTable(new MyTable<caret>Model());
    }
}
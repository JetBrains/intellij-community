import javax.swing.*;
import java.awt.*;

public class Bar {

    static class CellWrapper {
        public boolean isSeparator() {
            return true;
        }
    }

    static class MyRenderer extends DefaultListCellRenderer {
        public Component getListCellRendererComponent(JList list, Object value, int index, boolean s, boolean focus) {
            assert value instanceof CellWrapper;

            ((CellWrapper) value).isSeparator()<caret>

            mySelected = isSelected;

        }
    }

}


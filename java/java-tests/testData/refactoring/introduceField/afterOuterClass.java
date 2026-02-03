public class FieldTest {
    private Object comboBox;

    private class NoSelectionComboItem {
        public final Object comboBox;

        private NoSelectionComboItem() {
            comboBox = FieldTest.this.comboBox;
        }

        public String getLabel() {
            Object comboItem = comboBox.getSelectedItem();
            return comboItem == this ? "<Please Select>" : "<Back to overview>";
        }
    }
}